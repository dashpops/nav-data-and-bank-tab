package com.conde.hcimguide.ui;

import com.conde.hcimguide.HcimGuidePlugin;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.util.ImageUtil;
import com.conde.hcimguide.model.WithdrawItem;
import com.conde.hcimguide.service.WithdrawService;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.FontID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

/**
 * A button in the bank that filters it down to the items the current
 * "Withdraw:" step asks for — the same idea as Quest Helper's quest bank tab.
 *
 * <p>The bank rebuilds its item widgets constantly (opening, scrolling,
 * searching, switching tab), which wipes any changes made to them. So rather
 * than filtering once, this re-applies after every rebuild, hooked on
 * {@code BANKMAIN_FINISHBUILDING}. Turning it off restores the widget positions
 * saved when the filter was applied.
 */
@Singleton
public class WithdrawBankFilter
{
	private static final int BUTTON_SIZE = 25;
	// Heading bar, left of Quest Helper's button at x=408 so the two do not overlap.
	private static final int BUTTON_X = 380;
	private static final int BUTTON_Y = 5;
	/** Our own sprite slot. Negative ids do not collide with the game's. */
	private static final int ICON_SPRITE_ID = -1701;
	private static final int TICK_SPRITE_ID = SpriteID.Checkbox.CHECKED;
	private static final int CROSS_SPRITE_ID = SpriteID.Checkbox.CROSSED;
	private static final int TEXT_COLOR = 0xFF981F;
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_X_STEP = 48;
	private static final int ITEM_Y_STEP = 36;
	private static final int ITEM_ROW_START = 51;
	private static final int FIRST_ROW_Y = 0;

	private final Client client;
	private final ClientThread clientThread;
	private final HcimGuidePlugin plugin;
	private final WithdrawService withdrawService;

	private Widget button;
	private boolean active;
	/**
	 * Whether our button exists on the bank interface currently loaded. The bank
	 * finishes building many times per session — every open, scroll, search and
	 * tab change — so creating the button there leaks two widgets each time until
	 * the client stalls. It is created once per interface load instead.
	 */
	private boolean buttonAdded;
	/**
	 * Guards against re-entering the filter. Revalidating widgets and updating the
	 * scrollbar can make the bank build again, and building is what calls us — so
	 * without this a rebuild could feed itself and hang the client.
	 */
	private boolean applying;
	private boolean iconRegistered;
	/** Count/tick widgets added over bank items; rebuilt on each apply. */
	private final List<Widget> itemAnnotations = new ArrayList<>();

	/** widget index -> {originalX, originalY, hidden} captured before filtering. */
	private final Map<Integer, int[]> savedLayout = new HashMap<>();
	private int savedScrollHeight;
	private String savedTitle;

	@Inject
	private WithdrawBankFilter(Client client, ClientThread clientThread, HcimGuidePlugin plugin,
		WithdrawService withdrawService)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.plugin = plugin;
		this.withdrawService = withdrawService;
	}

	/** Called after the bank finishes building: (re)add the button and re-apply. */
	public void onBankBuilt()
	{
		// The rebuild replaced every widget, so anything saved refers to the old ones.
		savedLayout.clear();
		if (!buttonAdded)
		{
			addButton();
		}
		else
		{
			refreshButton();
		}
		if (active)
		{
			applyFilter();
		}
	}

	public void reset()
	{
		active = false;
		buttonAdded = false;
		itemAnnotations.clear();
		button = null;
		savedLayout.clear();
		savedTitle = null;
	}

	/** Publishes the sidebar icon as a sprite so a widget can use it. */
	private void registerIcon()
	{
		if (iconRegistered)
		{
			return;
		}
		client.getSpriteOverrides().put(ICON_SPRITE_ID,
			ImageUtil.getImageSpritePixels(HcimGuidePlugin.createIcon(), client));
		iconRegistered = true;
	}

	private void addButton()
	{
		registerIcon();
		Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null)
		{
			return;
		}

		button = parent.createChild(-1, WidgetType.GRAPHIC);
		button.setSpriteId(ICON_SPRITE_ID);
		button.setOriginalWidth(BUTTON_SIZE);
		button.setOriginalHeight(BUTTON_SIZE);
		button.setOriginalX(BUTTON_X);
		button.setOriginalY(BUTTON_Y);
		button.setHasListener(true);
		button.setAction(0, active ? "Show all items" : "Show guide items");
		button.setOnOpListener((JavaScriptCallback) e -> toggle());
		// Dimmed while inactive, full brightness when the filter is on.
		button.setOpacity(active ? 0 : 100);
		button.revalidate();
		buttonAdded = true;
	}

	/** Update the existing button in place; never creates. */
	private void refreshButton()
	{
		if (button != null)
		{
			button.setAction(0, active ? "Show all items" : "Show guide items");
		}
		if (button != null)
		{
			button.setOpacity(active ? 0 : 100);
		}
	}

	/**
	 * The bank interface was (re)loaded, so any widget we added to it is gone.
	 * Forget it, and let the next build create a single replacement.
	 */
	public void onBankInterfaceLoaded()
	{
		itemAnnotations.clear();   // belonged to the old interface; already gone
		buttonAdded = false;
		button = null;
		savedLayout.clear();
		savedTitle = null;
	}

	private void toggle()
	{
		active = !active;
		clientThread.invokeLater(() ->
		{
			if (active)
			{
				applyFilter();
			}
			else
			{
				restore();
			}
			refreshButton();
		});
	}

	/**
	 * Puts back everything {@link #applyFilter()} changed.
	 *
	 * <p>Done by remembering each widget's position and hidden flag rather than by
	 * asking the game to rebuild the bank: the rebuild script's arguments are not
	 * something I could verify, and getting them wrong breaks the bank interface.
	 * The saved state belongs to the build currently on screen, which is exactly
	 * the one being restored.
	 */
	private void restore()
	{
		clearAnnotations();
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null)
		{
			savedLayout.clear();
			return;
		}

		Widget[] children = container.getDynamicChildren();
		for (int i = 0; i < children.length; i++)
		{
			int[] saved = savedLayout.get(i);
			Widget child = children[i];
			if (saved == null || child == null)
			{
				continue;
			}
			child.setOriginalX(saved[0]);
			child.setOriginalY(saved[1]);
			child.setHidden(saved[2] == 1);
			child.revalidate();
		}
		savedLayout.clear();

		container.setScrollHeight(savedScrollHeight);
		client.runScript(ScriptID.UPDATE_SCROLLBAR,
			InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, 0);

		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (title != null && savedTitle != null)
		{
			title.setText(savedTitle);
			savedTitle = null;
		}
	}

	/**
	 * Hides bank items the step does not call for and packs the rest into a grid
	 * at the top, so the bank shows only what you came for.
	 */
	private void applyFilter()
	{
		if (applying)
		{
			return;
		}

		Set<Integer> wanted = wantedItemIds();
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (container == null || bank == null)
		{
			return;
		}

		clearAnnotations();
		applying = true;
		try
		{
			Widget[] children = container.getDynamicChildren();
			Item[] items = bank.getItems();
			int shown = 0;

			if (savedLayout.isEmpty())
			{
				savedScrollHeight = container.getScrollHeight();
				Widget existingTitle = client.getWidget(InterfaceID.Bankmain.TITLE);
				savedTitle = existingTitle == null ? null : existingTitle.getText();
			}

			for (int i = 0; i < children.length; i++)
			{
				Widget child = children[i];
				if (child == null)
				{
					continue;
				}

				savedLayout.putIfAbsent(i,
					new int[]{child.getOriginalX(), child.getOriginalY(), child.isHidden() ? 1 : 0});

				// Children beyond the item count are separators and tab furniture.
				boolean keep = i < items.length && wanted.contains(items[i].getId());
				if (!keep)
				{
					child.setHidden(true);
					continue;
				}

				child.setHidden(false);
				child.setOriginalX(ITEM_ROW_START + (shown % ITEMS_PER_ROW) * ITEM_X_STEP);
				child.setOriginalY(FIRST_ROW_Y + (shown / ITEMS_PER_ROW) * ITEM_Y_STEP);
				child.revalidate();
				shown++;
			}

			// Shrink the scroll area to what is left, or the bank scrolls into blank space.
			int rows = (shown + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
			container.setScrollHeight(Math.max(rows * ITEM_Y_STEP, container.getHeight()));
			client.runScript(ScriptID.UPDATE_SCROLLBAR,
				InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, 0);

			Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
			if (title != null)
			{
				title.setText(shown > 0
					? "Guide items (<col=ff9040>" + shown + "</col>)"
					: "Guide items — <col=ff4040>none in bank</col>");
			}
		}
		finally
		{
			applying = false;
		}
	}

	/**
	 * Draws "/ N" and a tick or cross over a bank item, the way Quest Helper does:
	 * the count sits just right of the stack size the bank already draws, and the
	 * marker after it, dropping to a second line when the pair would be too wide.
	 *
	 * <p>The left-hand number is what you are carrying, not what is banked, so it
	 * counts up as you withdraw and the cross becomes a tick when the step is met.
	 */
	private void annotate(Widget container, int itemId, int bankQuantity, int baseX, int baseY)
	{
		WithdrawItem entry = entryFor(itemId);
		if (entry == null || entry.getQuantity() <= 0)
		{
			return;
		}

		int required = entry.getQuantity();
		int carried = withdrawService.carriedCount(entry);
		boolean satisfied = carried >= required;

		String goal = QuantityFormatter.quantityToStackSize(required);
		String have = QuantityFormatter.quantityToStackSize(carried);
		int goalLength = (int) Math.round(goal.length() * 5.5);
		int haveLength = have.length() * 6;

		int textX = baseX + 2 + haveLength;
		int textY = baseY - 1;
		int spriteX = textX + goalLength + 10;
		int spriteY = textY;
		if (haveLength + goalLength > 20)
		{
			textX = baseX;
			textY = baseY + 9;
			spriteX = baseX + 2 + haveLength;
			spriteY = baseY - 1;
		}

		itemAnnotations.add(createText(container, have + "/" + goal, textX, textY));
		itemAnnotations.add(createSprite(container,
			satisfied ? TICK_SPRITE_ID : CROSS_SPRITE_ID, spriteX, spriteY));
	}

	private Widget createText(Widget container, String text, int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setText(text);
		widget.setTextColor(TEXT_COLOR);
		widget.setFontId(FontID.PLAIN_11);
		widget.setTextShadowed(true);
		widget.setOriginalWidth(50);
		widget.setOriginalHeight(20);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.revalidate();
		return widget;
	}

	private Widget createSprite(Widget container, int spriteId, int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.GRAPHIC);
		widget.setSpriteId(spriteId);
		widget.setOriginalWidth(11);
		widget.setOriginalHeight(11);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.revalidate();
		return widget;
	}

	/** Remove the widgets we drew last time, or they stack up on every rebuild. */
	private void clearAnnotations()
	{
		for (Widget widget : itemAnnotations)
		{
			if (widget != null)
			{
				widget.setHidden(true);
			}
		}
		itemAnnotations.clear();
	}

	/** The withdraw entry this item satisfies, or null. */
	private WithdrawItem entryFor(int itemId)
	{
		for (WithdrawItem entry : plugin.getCurrentWithdrawItems())
		{
			if (entry.getItemIds().contains(itemId))
			{
				return entry;
			}
		}
		return null;
	}

	/** Item IDs the current step still needs; empty when nothing is outstanding. */
	private Set<Integer> wantedItemIds()
	{
		List<WithdrawItem> wanted = plugin.getCurrentWithdrawItems();
		return withdrawService.outstandingItemIds(wanted);
	}

	public boolean isActive()
	{
		return active;
	}
}
