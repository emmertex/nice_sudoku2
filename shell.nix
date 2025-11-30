{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  buildInputs = with pkgs; [
    jdk17
    nodejs
    gradle
    tmux
  ];

  shellHook = ''
    export JAVA_HOME=${pkgs.jdk17.home}
    export PATH="$JAVA_HOME/bin:$PATH"
  '';
}
