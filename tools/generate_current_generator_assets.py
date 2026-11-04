"""Use the existing deep-sea palette workflow for the native turbine geometry."""
from generate_deepsea_content_assets import ROOT, asset

if __name__ == '__main__':
    for source, suffix in [('turbine-condenser', ''), ('turbine-condenser-rotator', '-rotator')]:
        asset(f'blocks/power/{source}.png',
              ROOT / f'sprites/blocks/power/underice-current-generator{suffix}.png',
              (94, 167, 181), (64, 64))
