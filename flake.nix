{
  description = "A Kotlin parser combinator framework";

  inputs = 
    { 
      nixpkgs.url = "nixpkgs/nixos-24.05";
    };

  outputs = inputs @ { self, nixpkgs }:
  let 
    pkgs-conf = {
      system = "x86_64-linux";
      config.allowUnfree = true;
    };

    pkgs = import nixpkgs pkgs-conf;

  in rec {
    packages.x86_64-linux = rec {
      parsec  = pkgs.callPackage ./default.nix {};
      default = parsec;
    };
  };
}
