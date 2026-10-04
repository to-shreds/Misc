# Source and interface notes

Reviewed during this build on October 4, 2026. These references support interface choices, not a claim of live validation of this project.

## Tasker

User-supplied Regex_Scene.prj.xml: TaskerData tv=6.7.6-beta and device metrics 1440.0,3120.0; native task, profile, Scene, and action argument examples. Original omitted from release. New IDs and names are generated independently.

Official Java Code documentation: https://tasker.joaoapps.com/userguide/en/help/ah_java_code.html

Official Web element documentation: https://tasker.joaoapps.com/userguide/en/element_web.html

Taskomater's recorded XML action/event/state codes: https://github.com/Taskomater/Tasker-XML-Info/blob/master/Tasker_XML_Codes.md

Primary author examples supplement the user's export for XML constructs absent from that sample: https://gist.github.com/Raj9039852537/15bdb3f5be5e6901e901e5e16608bfe8 and https://gist.github.com/sbougerel/0cb4630962159ded1e7a703352dc9718

The recorded code list is older than the target Tasker version. Structural consistency and syntax checks do not prove import/runtime behavior. That phone check remains necessary.

## Hyundai library

Public package release selected for optional installation: https://pypi.org/project/hyundai-kia-connect-api/4.35.0/

Its documentation requires Python 3.12 or newer and maps region=3 to USA, brand=2 to Hyundai. Those numeric mappings are used, not the similarly named string constants.

VehicleManager source: https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api/blob/master/hyundai_kia_connect_api/VehicleManager.py

Read blob SHA: 1788099627e978ea820dc1d29a4526928e98b444.

Vehicle model: https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api/blob/master/hyundai_kia_connect_api/Vehicle.py

Read blob SHA: b8e98e551b587a2ea70fe5f9841711fda8b57eb8.

Constants: https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api/blob/master/hyundai_kia_connect_api/const.py

Read blob SHA: 3f9fe5344d29fed31aa015ca7c534b587c1d3b86.

U.S. implementation: https://github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api/blob/master/hyundai_kia_connect_api/HyundaiBlueLinkApiUSA.py

The reviewed default-branch source and selected PyPI release are distinct reference points. The dependency was not installed here because container package downloads failed. Fake-library contract tests cover intended calls, not the actual distribution or Hyundai service. Do not equate method existence with VIN-specific support.

## Termux

Official Termux:Boot instructions: https://github.com/termux/termux-boot/blob/master/README.md

Boot is optional and distinct from Termux:Tasker. This release includes manual foreground startup, not a boot installation or permanent wake lock.
