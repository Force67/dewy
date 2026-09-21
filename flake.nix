{
  description = "dewy: steer your coding agents from your phone";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config.allowUnfree = true;
        config.android_sdk.accept_license = true;
      };
      buildTools = "36.0.0";
      android = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "37" ];
        buildToolsVersions = [ buildTools ];
        includeEmulator = false;
      };
      sdk = "${android.androidsdk}/libexec/android-sdk";
    in {
      devShells.${system}.default = pkgs.mkShell {
        # akiko links through cc; it comes from a source build of
        # github.com/aki-language/akiv4 (see README.md).
        packages = [ pkgs.gcc pkgs.jdk21 pkgs.gradle android.androidsdk ];
        ANDROID_HOME = sdk;
        JAVA_HOME = pkgs.jdk21.home;
        # AGP fetches a generic aapt2 that NixOS cannot run; use the SDK's.
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${sdk}/build-tools/${buildTools}/aapt2";
      };
    };
}
