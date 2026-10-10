"""Offline protocol checks. No real Hyundai account or vehicle is contacted."""

import json
import unittest
from unittest.mock import patch

import hyundai
from hyundai import HyundaiClient, HyundaiError

EMAIL = "driver+emergency@example.invalid"
VIN = "TESTVIN12345678901"
REGID = "registration-placeholder"
VEHICLE = {"regid": REGID, "vin": VIN, "modelName": "Santa Fe Hybrid", "nickName": "Private Name",
           "evStatus": "N", "vehicleGeneration": 3, "enrollmentStatus": "ACTIVE"}
STATUS = {"dateTime": "20261010123501", "doorLock": True, "engine": False, "airCtrlOn": False,
          "fuelLevel": 55, "dte": {"value": 250, "unit": 1}, "odometer": 1200,
          "battery": {"batSoc": 83}, "vehicleLocation": {"latitude": 99, "longitude": 11},
          "unexpectedPrivateField": EMAIL}


def reply(data=None, status=200, headers=None):
    return status, headers or {}, b"" if data is None else json.dumps(data).encode()


class FakeTransport:
    def __init__(self, *responses):
        self.responses = list(responses)
        self.calls = []

    def __call__(self, method, path, headers, body):
        self.calls.append({"method": method, "path": path, "headers": dict(headers),
                           "body": None if body is None else json.loads(body)})
        if not self.responses:
            raise AssertionError("Unexpected request, including a potential blind retry")
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response


def ready_client(*extra_responses, pin="1234", vehicle=None):
    transport = FakeTransport(
        reply({"access_token": "memory-token", "expires_in": 1800}),
        reply({"enrolledVehicleDetails": [{"vehicleDetails": vehicle or dict(VEHICLE)}]}),
        reply({"vehicleStatus": dict(STATUS)}),
        *extra_responses,
    )
    client = HyundaiClient(EMAIL, "private-password", pin, transport=transport)
    client.connect()
    client.cached_status()
    return client, transport


class HyundaiClientTests(unittest.TestCase):
    def test_confirmed_encoding_and_headers(self):
        client, transport = ready_client()
        self.assertEqual(transport.calls[0]["path"], "/v2/ac/oauth/token")
        self.assertNotIn("accessToken", transport.calls[0]["headers"])
        self.assertEqual(transport.calls[1]["path"], "/ac/v2/enrollment/details/driver%2Bemergency@example.invalid")
        status_call = transport.calls[2]
        self.assertEqual(status_call["headers"]["registrationId"], REGID)
        self.assertEqual(status_call["headers"]["vin"], VIN)
        self.assertEqual(status_call["headers"]["gen"], "3")
        self.assertEqual(status_call["headers"]["refresh"], "false")
        self.assertIn(status_call["headers"]["offset"], {"-4", "-5"})
        self.assertNotIn("Cookie", status_call["headers"])
        self.assertIsNotNone(client._token)

    def test_safe_vehicle_output_and_status_freshness(self):
        client, transport = ready_client(reply({"vehicleStatus": dict(STATUS)}))
        result = client.cached_status()
        self.assertEqual(result["vehicle_timestamp"], STATUS["dateTime"])
        self.assertEqual(result["freshness"], "cached_observation")
        self.assertEqual(result["fuel_percent"], 55)
        self.assertEqual(result["battery_percent"], 83)
        self.assertIs(result["cached"], True)
        serialized = json.dumps(result)
        self.assertNotIn(EMAIL, serialized)
        self.assertNotIn(VIN, serialized)
        self.assertNotIn(REGID, serialized)
        self.assertNotIn("Private Name", serialized)
        self.assertNotIn("latitude", serialized)
        self.assertNotEqual(result["vehicle_timestamp"], result["read_at"])

    def test_active_vehicle_selection_only(self):
        transport = FakeTransport(reply({"access_token": "token"}), reply({"enrolledVehicleDetails": [
            {"vehicleDetails": {**VEHICLE, "regid": "cancelled", "enrollmentStatus": "CANCELLED"}},
            {"vehicleDetails": dict(VEHICLE)},
        ]}))
        client = HyundaiClient(EMAIL, "password", "1234", transport=transport)
        result = client.connect()
        self.assertEqual(len(result["vehicles"]), 1)
        self.assertEqual(result["selected"], "0")

    def test_multiple_vehicles_requires_explicit_selection(self):
        transport = FakeTransport(reply({"access_token": "token"}), reply({"enrolledVehicleDetails": [
            {"vehicleDetails": dict(VEHICLE)}, {"vehicleDetails": {**VEHICLE, "regid": "second"}},
        ]}))
        client = HyundaiClient(EMAIL, "password", "1234", transport=transport)
        self.assertIsNone(client.connect()["selected"])
        with self.assertRaises(HyundaiError):
            client.cached_status()
        self.assertEqual(client.select_vehicle("1")["selected"], "1")

    def test_unset_pin_allows_reads_and_requires_pin_for_command(self):
        client, transport = ready_client(reply(None, headers={"TmsTid": "transaction-one"}), pin="")
        self.assertNotIn("blueLinkServicePin", transport.calls[2]["headers"])
        with self.assertRaises(HyundaiError):
            client.command("unlock")
        client.command("unlock", "2345")
        self.assertEqual(transport.calls[-1]["headers"]["blueLinkServicePin"], "2345")
        self.assertEqual(client._pin, "")

    def test_lock_and_unlock_exact_recipe(self):
        for action, suffix in (("lock", "off"), ("unlock", "on")):
            with self.subTest(action=action):
                client, transport = ready_client(reply(None, headers={"TmsTid": "transaction-one"}))
                self.assertEqual(client.command(action), "transaction-one")
                call = transport.calls[-1]
                self.assertEqual(call["method"], "POST")
                self.assertEqual(call["path"], "/ac/v2/rcs/rdo/" + suffix)
                self.assertEqual(call["body"], {"userName": EMAIL, "vin": VIN})
                self.assertEqual(call["headers"]["APPCLOUD-VIN"], VIN)

    def test_start_presets_reuse_regid_body_mapping(self):
        for action, temperature, defrost in (("start_regular", 72, False), ("start_cold", 62, False), ("start_hot", 81, True)):
            with self.subTest(action=action):
                client, transport = ready_client(reply(None, headers={"tmstid": "start-transaction"}))
                client.command(action)
                call = transport.calls[-1]
                self.assertEqual(call["path"], "/ac/v2/rcs/rsc/start")
                self.assertEqual(call["body"]["vin"], REGID)
                self.assertEqual(call["headers"]["vin"], VIN)
                self.assertEqual(call["body"]["airTemp"], {"unit": 1, "value": temperature})
                self.assertEqual(call["body"]["defrost"], defrost)
                self.assertEqual(call["body"]["igniOnDuration"], 10)
                self.assertEqual(call["body"]["seatHeaterVentInfo"], {
                    "drvSeatHeatState": 0, "astSeatHeatState": 0, "rlSeatHeatState": 0, "rrSeatHeatState": 0})

    def test_stop_body_is_absent(self):
        client, transport = ready_client(reply(None, headers={"tmstid": "stop-transaction"}))
        client.command("stop")
        self.assertEqual(transport.calls[-1]["path"], "/ac/v2/rcs/rsc/stop")
        self.assertIsNone(transport.calls[-1]["body"])

    def test_unconfirmed_engine_does_not_send_start(self):
        for changes in ({"evStatus": "E"}, {"vehicleGeneration": 2}, {"evStatus": None}):
            client, transport = ready_client(vehicle={**VEHICLE, **changes})
            with self.assertRaises(HyundaiError):
                client.command("start_regular")
            self.assertEqual(len(transport.calls), 3)

    def test_all_command_poll_headers_and_normalized_outcomes(self):
        for action in hyundai.ACTIONS:
            with self.subTest(action=action):
                client, transport = ready_client(reply(None, headers={"tmstid": "tid-one"}),
                                                reply({"status": "PENDING"}), reply({"status": "SUCCESS"}))
                tid = client.command(action)
                self.assertEqual(client.poll(tid)["state"], "pending")
                with self.assertRaises(HyundaiError):
                    client.command("lock")
                self.assertEqual(client.poll(tid)["state"], "success")
                for call in transport.calls[-2:]:
                    self.assertEqual(call["path"], "/ac/v2/rmt/getRunningStatus")
                    self.assertEqual(call["headers"]["service_type"], "REMOTE_POLL")
                    self.assertEqual(call["headers"]["tid"], tid)
                    self.assertEqual(call["headers"]["login_id"], EMAIL)

    def test_failure_and_unknown_results(self):
        for upstream, expected in (("ERROR", "failure"), ("NEW_UNKNOWN_VALUE", "unknown"), (None, "unknown")):
            client, transport = ready_client(reply(None, headers={"tmstid": "tid-one"}), reply({"status": upstream}))
            result = client.poll(client.command("lock"))
            self.assertEqual(result["state"], expected)
            self.assertNotIn("NEW_UNKNOWN_VALUE", result["message"])

    def test_failed_command_never_reauthenticates_or_resends(self):
        client, transport = ready_client(reply({"errorMessage": "private-password " + EMAIL}, status=401))
        with self.assertRaises(HyundaiError) as caught:
            client.command("unlock")
        self.assertEqual(caught.exception.code, "authentication")
        self.assertEqual(len(transport.calls), 4)
        self.assertNotIn("private-password", str(caught.exception))
        self.assertNotIn(EMAIL, str(caught.exception))

    def test_read_can_reauthenticate_once(self):
        client, transport = ready_client(reply(None, status=401),
                                        reply({"access_token": "fresh-token", "expires_in": 1800}),
                                        reply({"vehicleStatus": dict(STATUS)}))
        self.assertIs(client.cached_status()["door_locked"], True)
        self.assertEqual([c["method"] for c in transport.calls[-3:]], ["GET", "POST", "GET"])
        self.assertEqual(transport.calls[-1]["headers"]["accessToken"], "fresh-token")

    def test_unknown_command_guard_blocks_resend_until_acknowledged(self):
        client, transport = ready_client(TimeoutError("private-password " + EMAIL),
                                        reply(None, headers={"tmstid": "tid-next"}))
        with self.assertRaises(HyundaiError) as caught:
            client.command("unlock")
        self.assertTrue(caught.exception.uncertain)
        self.assertNotIn(EMAIL, str(caught.exception))
        with self.assertRaises(HyundaiError):
            client.command("unlock")
        self.assertEqual(len(transport.calls), 4)
        client.acknowledge_unknown()
        self.assertEqual(client.command("lock"), "tid-next")

    def test_missing_tid_stays_unknown(self):
        client, transport = ready_client(reply(None))
        with self.assertRaises(HyundaiError) as caught:
            client.command("unlock")
        self.assertTrue(caught.exception.uncertain)
        self.assertEqual(caught.exception.code, "unknown_command")
        with self.assertRaises(HyundaiError):
            client.command("lock")
        self.assertEqual(len(transport.calls), 4)

    def test_reconnect_cannot_discard_pending_poll_identity(self):
        client, transport = ready_client(reply(None, headers={"tmstid": "tid-one"}), reply({"status": "SUCCESS"}))
        tid = client.command("lock")
        with self.assertRaises(HyundaiError) as caught:
            client.connect()
        self.assertEqual(caught.exception.code, "pending_command")
        self.assertEqual(len(transport.calls), 4)
        self.assertEqual(client.poll(tid)["state"], "success")

    def test_non_json_response_does_not_leak_upstream_text(self):
        client, transport = ready_client((500, {}, b"private-password " + EMAIL.encode()))
        with self.assertRaises(HyundaiError) as caught:
            client.command("unlock")
        self.assertTrue(caught.exception.uncertain)
        self.assertNotIn("private-password", str(caught.exception))

    def test_bad_token_or_pin_cannot_inject_headers(self):
        with self.assertRaises(HyundaiError):
            HyundaiClient(EMAIL, "password", "1\r\n2")
        transport = FakeTransport(reply({"access_token": "token\r\nX: bad"}))
        with self.assertRaises(HyundaiError):
            HyundaiClient(EMAIL, "password", "1234", transport=transport).connect()

    def test_unknown_status_values_remain_unknown(self):
        status = {**STATUS, "doorLock": "false", "fuelLevel": EMAIL, "dateTime": EMAIL}
        client, transport = ready_client(reply({"vehicleStatus": status}))
        result = client.cached_status()
        self.assertIsNone(result["door_locked"])
        self.assertIsNone(result["fuel_percent"])
        self.assertIsNone(result["vehicle_timestamp"])


class FixedTransportTests(unittest.TestCase):
    def fake_connection(self, response_status=200, response_headers=None, chunks=None):
        response = unittest.mock.Mock()
        response.status = response_status
        response.getheaders.return_value = response_headers or []
        response.read1.side_effect = chunks or [b"{}", b""]
        connection = unittest.mock.Mock()
        connection.sock = unittest.mock.Mock()
        connection.getresponse.return_value = response
        return connection, response

    def test_fixed_host_and_no_redirect_following(self):
        connection, response = self.fake_connection(302, [("Location", "https://evil.example.invalid/")])
        with patch("hyundai.http.client.HTTPSConnection", return_value=connection) as constructor:
            status, headers, body = hyundai._https_request("GET", "/ac/v2/rcs/rvs/vehicleStatus", {}, None)
        constructor.assert_called_once_with(hyundai.HOST, timeout=hyundai.REQUEST_TIMEOUT)
        self.assertEqual(status, 302)
        connection.request.assert_called_once()
        connection.close.assert_called_once()

    def test_oversize_content_length_rejected_before_read(self):
        connection, response = self.fake_connection(response_headers=[("Content-Length", str(hyundai.MAX_RESPONSE_BYTES + 1))])
        with patch("hyundai.http.client.HTTPSConnection", return_value=connection):
            with self.assertRaises(HyundaiError):
                hyundai._https_request("GET", "/safe", {}, None)
        response.read1.assert_not_called()
        connection.close.assert_called_once()

    def test_oversize_stream_rejected(self):
        connection, response = self.fake_connection(chunks=[b"X" * (hyundai.MAX_RESPONSE_BYTES + 1)])
        with patch("hyundai.http.client.HTTPSConnection", return_value=connection):
            with self.assertRaises(HyundaiError):
                hyundai._https_request("GET", "/safe", {}, None)
        connection.close.assert_called_once()


if __name__ == "__main__":
    unittest.main()
