package com.conde.hcimguide.service;

import com.conde.hcimguide.model.WithdrawItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;

/**
 * Works out which items of a "Withdraw:" step you are still short of.
 *
 * <p>Counts what you are carrying — inventory and worn equipment — and subtracts
 * it, so a step stops nagging once you have the items. An entry naming
 * alternatives ("Food": wine or karambwan) counts them together, because any of
 * them satisfies it.
 */
@Singleton
public class WithdrawService
{
	private final Client client;

	@Inject
	public WithdrawService(Client client)
	{
		this.client = client;
	}

	/** How many of the given item IDs the player is carrying, worn items included. */
	private int carried(Set<Integer> itemIds)
	{
		int total = 0;
		for (int containerId : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
			{
				continue;
			}
			for (Item item : container.getItems())
			{
				if (itemIds.contains(item.getId()))
				{
					total += Math.max(1, item.getQuantity());
				}
			}
		}
		return total;
	}

	/**
	 * Entries still outstanding for this step. Empty means you already have
	 * everything, in which case nothing should be drawn.
	 *
	 * <p>Must be called on the client thread — it reads item containers.
	 */
	public List<WithdrawItem> outstanding(List<WithdrawItem> wanted)
	{
		if (wanted == null || wanted.isEmpty())
		{
			return Collections.emptyList();
		}

		List<WithdrawItem> missing = new ArrayList<>();
		for (WithdrawItem item : wanted)
		{
			Set<Integer> ids = new HashSet<>(item.getItemIds());
			if (ids.isEmpty())
			{
				// No IDs recorded: we cannot check, so always show it.
				missing.add(item);
				continue;
			}
			int have = carried(ids);
			// Quantity 0 means "some, amount unstated" - one is enough to satisfy it.
			int need = item.getQuantity() > 0 ? item.getQuantity() : 1;
			if (have < need)
			{
				missing.add(item);
			}
		}
		return missing;
	}

	/** Every item ID that would help satisfy the outstanding entries. */
	public Set<Integer> outstandingItemIds(List<WithdrawItem> wanted)
	{
		Set<Integer> ids = new HashSet<>();
		for (WithdrawItem item : outstanding(wanted))
		{
			ids.addAll(item.getItemIds());
		}
		return ids;
	}

	/** "Withdraw: 4 x Flax, Food" — null when there is nothing outstanding. */
	public String describe(List<WithdrawItem> wanted)
	{
		List<WithdrawItem> missing = outstanding(wanted);
		if (missing.isEmpty())
		{
			return null;
		}
		List<String> parts = new ArrayList<>(missing.size());
		for (WithdrawItem item : missing)
		{
			parts.add(item.describe());
		}
		return "Withdraw: " + String.join(", ", parts);
	}
}
