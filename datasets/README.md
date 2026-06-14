# YYYP SteamDT Sample Datasets

These CSV files are a compact assignment sample exported from the local YYYP SQLite database (`yyyp.db`). The original data was collected from the SteamDT CS2 market API and contains latest observations for nine trading platforms: YOUPIN, C5, BUFF, HALOSKINS, STEAM, SKINPORT, WAXPEER, DMARKET, and CSMONEY.

The price files are separated by platform to represent independent market sources. Some platforms contain zero prices in this snapshot; the Java integration keeps those observations but marks positive prices as valid market prices for analytical queries. Items are resolved using Steam market hash names and available platform-specific item IDs.
