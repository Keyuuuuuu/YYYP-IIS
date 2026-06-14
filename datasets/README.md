# YYYP SteamDT Sample Datasets

These CSV files are an assignment sample exported from the local YYYP SQLite database (`yyyp.db`). The original observations were collected from the SteamDT CS2 market API.

The dataset contains 10,000 CS2 items and the latest valid positive sell-price observations from five market platforms that are actually present with usable prices in the local snapshot: YOUPIN, C5, BUFF, HALOSKINS, and STEAM. Platforms whose latest collected prices were entirely zero or unavailable were excluded from this assignment dataset.

The price files are separated by platform to represent independent market sources. Items are integrated by the Java application using platform-specific item IDs when available and Steam market hash names as the fallback identifier.
