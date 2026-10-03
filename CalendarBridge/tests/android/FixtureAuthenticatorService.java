package com.jon.calendarbridge.tests;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

/** Emulator-only account identity. It has no credentials, auth tokens, or network access. */
public final class FixtureAuthenticatorService extends Service {
    private AbstractAccountAuthenticator authenticator;
    @Override public void onCreate() {
        super.onCreate();
        authenticator = new AbstractAccountAuthenticator(this) {
            private Bundle unavailable() { Bundle b=new Bundle();b.putInt(AccountManager.KEY_ERROR_CODE,AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION);b.putString(AccountManager.KEY_ERROR_MESSAGE,"Isolated fixture account");return b; }
            @Override public Bundle editProperties(AccountAuthenticatorResponse r,String type){return unavailable();}
            @Override public Bundle addAccount(AccountAuthenticatorResponse r,String type,String token,String[] features,Bundle options){return unavailable();}
            @Override public Bundle confirmCredentials(AccountAuthenticatorResponse r,Account account,Bundle options){return unavailable();}
            @Override public Bundle getAuthToken(AccountAuthenticatorResponse r,Account account,String type,Bundle options){return unavailable();}
            @Override public String getAuthTokenLabel(String type){return "No fixture tokens";}
            @Override public Bundle updateCredentials(AccountAuthenticatorResponse r,Account account,String type,Bundle options){return unavailable();}
            @Override public Bundle hasFeatures(AccountAuthenticatorResponse r,Account account,String[] features){Bundle b=new Bundle();b.putBoolean(AccountManager.KEY_BOOLEAN_RESULT,false);return b;}
        };
    }
    @Override public IBinder onBind(Intent intent){return authenticator.getIBinder();}
}
