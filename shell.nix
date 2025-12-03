{ pkgs ? import <nixpkgs> {} }:

let
  androidSdk = pkgs.androidenv.composeAndroidPackages {
    cmdLineToolsVersion = "latest";
    toolsVersion = "26.1.1";
    platformToolsVersion = "35.0.1";
    buildToolsVersions = ["35.0.0"];
    includeEmulator = false;
    includeSources = false;
    includeSystemImages = false;
    systemImageTypes = [];
    abiVersions = [];
    cmakeVersions = [];
    includeNDK = false;
    useGoogleAPIs = false;
    useGoogleTVAddOns = false;
    includeExtras = [];
    platformVersions = ["35"];
  };
in

pkgs.mkShell {
  buildInputs = with pkgs; [
    jdk17
    nodejs
    gradle
    tmux
#    androidSdk.androidsdk
  ];

 # shellHook = ''
  #  export JAVA_HOME=${pkgs.jdk17.home}
#    export ANDROID_HOME=${androidSdk.androidsdk}/libexec/android-sdk
 #   export PATH="$JAVA_HOME/bin:$PATH"
  #  export PATH="$ANDROID_HOME/platform-tools:$PATH"
   # export PATH="$ANDROID_HOME/tools:$PATH"
#    export PATH="$ANDROID_HOME/tools/bin:$PATH"
 # '';
}

