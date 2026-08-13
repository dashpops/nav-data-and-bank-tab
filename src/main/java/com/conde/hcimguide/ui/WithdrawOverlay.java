package com.conde.hcimguide.ui;

import com.conde.hcimguide.HcimGuideConfig;
import com.conde.hcimguide.HcimGuidePlugin;
import com.conde.hcimguide.model.WithdrawItem;
import com.conde.hcimguide.service.WithdrawService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * Shows what a "Withdraw:" step still needs.
 *
 * <p>Two halves, mirroring how the neighbouring plugins do it: text above your
 * head — the same idea as Shortest Path's "Pick up: ..." — and, when the bank is
 * open, a box around the items you are there for, which is a lighter way to get
 * Quest Helper's bank-filter effect without rewriting the bank interface.
 *
 * <p>Both disappear once you are carrying everything, so a step you have already
 * done stops shouting.
 */
public class WithdrawOverlay extends Overlay
{
	private static final Color TEXT_COLOR = new Color(236, 197, 94);
	private static final Color HIGHLIGHT_COLOR = new Color(236, 197, 94, 220);
	private static final Color HIGHLIGHT_FILL = new Color(236, 197, 94, 45);
	private static final int PLAYER_TEXT_HEIGHT = 220;

	private final Client client;
	private final HcimGuidePlugin plugin;
	private final HcimGuideConfig config;
	private final WithdrawService withdrawService;

	@Inject
	private WithdrawOverlay(Client client, HcimGuidePlugin plugin, HcimGuideConfig config,
		WithdrawService withdrawService)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.withdrawService = withdrawService;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showWithdrawItems())
		{
			return null;
		}

		List<WithdrawItem> wanted = plugin.getCurrentWithdrawItems();
		if (wanted.isEmpty())
		{
			return null;
		}

		String text = withdrawService.describe(wanted);
		if (text == null)
		{
			return null;   // already carrying everything
		}

		drawAbovePlayer(graphics, text);
		highlightBankItems(graphics, withdrawService.outstandingItemIds(wanted));
		return null;
	}

	private void drawAbovePlayer(Graphics2D graphics, String text)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		Point point = local.getCanvasTextLocation(graphics, text, PLAYER_TEXT_HEIGHT);
		if (point != null)
		{
			OverlayUtil.renderTextLocation(graphics, point, text, TEXT_COLOR);
		}
	}

	/**
	 * Boxes the wanted items in an open bank. Bank item widgets are children of
	 * the item container in slot order, so the child index matches the container
	 * index; hidden children are the empty tail of the tab.
	 */
	private void highlightBankItems(Graphics2D graphics, Set<Integer> itemIds)
	{
		if (itemIds.isEmpty())
		{
			return;
		}

		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null || container.isHidden())
		{
			return;
		}

		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (bank == null)
		{
			return;
		}

		Widget[] children = container.getDynamicChildren();
		Item[] items = bank.getItems();
		int count = Math.min(children.length, items.length);
		for (int i = 0; i < count; i++)
		{
			if (!itemIds.contains(items[i].getId()))
			{
				continue;
			}
			Widget child = children[i];
			if (child == null || child.isHidden())
			{
				continue;
			}
			Rectangle bounds = child.getBounds();
			graphics.setColor(HIGHLIGHT_FILL);
			graphics.fill(bounds);
			graphics.setColor(HIGHLIGHT_COLOR);
			graphics.draw(bounds);
		}
	}
}
