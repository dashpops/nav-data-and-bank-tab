package com.conde.hcimguide.model;

import java.util.Collections;
import java.util.List;

/**
 * One thing a "Withdraw:" step asks you to take out of the bank.
 *
 * <p>A single entry can accept several items, because the guide often names a
 * role rather than an item — "Food" is satisfied by a Jug of wine or a
 * Karambwan. Any one of {@link #itemIds} counts, and they are counted together
 * towards {@link #quantity}, so 4 flax is met by 4 flax and "food" by whichever
 * food you happen to have.
 */
public class WithdrawItem
{
	/** What to show the player, e.g. "Food" or "Flax". */
	private String name;

	/** Item IDs that satisfy this entry. Any one of them will do. */
	private List<Integer> itemIds;

	/** How many are needed. 0 or absent means "some" — quantity not stated. */
	private int quantity;

	public String getName()
	{
		return name;
	}

	public List<Integer> getItemIds()
	{
		return itemIds == null ? Collections.emptyList() : itemIds;
	}

	public int getQuantity()
	{
		return quantity;
	}

	/** True when this entry names alternatives rather than one specific item. */
	public boolean hasAlternatives()
	{
		return getItemIds().size() > 1;
	}

	/** "4 x Flax", or just "Food" when no quantity is given. */
	public String describe()
	{
		return quantity > 0 ? quantity + " x " + name : name;
	}
}
