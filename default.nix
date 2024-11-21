{ lib, pkgs, stdenv, gradle, jdk21, jdk17 }:

stdenv.mkDerivation rec {
  pname   = "kotlin-parsec";
  version = "latest";
  name    = "${pname}-${version}";
  src     = ./.;

  buildInputs = [
    (gradle.override {
      javaToolchains = [ "${jdk17}/lib/openjdk" ];
    })
    jdk17
  ];
}
