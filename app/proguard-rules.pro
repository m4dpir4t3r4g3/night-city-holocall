# Hilt, Compose, DataStore and AndroidX ship their own consumer rules.

# Services/receivers are referenced from the manifest and are kept by AAPT rules.
# Audio cue resources are resolved by name at runtime; see res/raw/keep.xml, which
# protects them from resource shrinking.
