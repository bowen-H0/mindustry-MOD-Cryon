"""Extend the existing Cryon processor palette with native processor geometry."""
from generate_deepsea_content_assets import ROOT, asset

if __name__ == '__main__':
    asset('blocks/logic/hyper-processor.png',
          ROOT / 'sprites/blocks/quantum-communication-computer.png',
          (105, 164, 183), (160, 160))
