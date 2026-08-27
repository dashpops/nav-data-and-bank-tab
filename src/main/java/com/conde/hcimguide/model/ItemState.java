package com.conde.hcimguide.model;

/** Where a "Withdraw:" item currently is, for the sidebar's colour coding. */
public enum ItemState
{
	/** On you — inventory or worn — in the amount the step needs. Green. */
	CARRIED,
	/** Not on you, but sitting in the bank ready to withdraw. White. */
	BANK,
	/** Not on you and not (enough) in the bank. Red. */
	MISSING,
	/** No item id recorded, so it cannot be checked — shown blue as the exact guide text. */
	UNKNOWN
}
