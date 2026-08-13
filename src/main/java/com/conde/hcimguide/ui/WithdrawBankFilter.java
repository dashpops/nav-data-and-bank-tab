package com.conde.hcimguide.ui;

import com.conde.hcimguide.HcimGuidePlugin;
import com.conde.hcimguide.service.WithdrawService;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A button in the bank heading that filters it to the items the current
 * "Withdraw:" step asks for, in the spirit of Quest Helper's quest bank tab.
 *
 * <p>Deliberately narrow about what it touches: it only hides and moves the
 * bank's own item widgets, and never adds children to the item container.
 * Adding children there caused two faults — they were only hidden rather than
 * removed, so they piled up until the client stalled, and they join
 * {@code getDynamicChildren()}, which broke the index-to-item mapping this
 * relies on. Counts and ticks are drawn by {@link WithdrawOverlay} instead,
 * where no widgets are involved.
 *
 * <p>The bank rebuilds its item widgets on every open, scroll, search and tab
 * change, discarding anything changed on them, so the filter re-applies on
 * {@code BANKMAIN_FINISHBUILDING} rather than once.
 */
@Singleton
public class WithdrawBankFilter
{
	private static final Logger log = LoggerFactory.getLogger(WithdrawBankFilter.class);

	private static final int BUTTON_SIZE = 25;
	// Heading bar, left of Quest Helper's button at x=408 so the two can coexist.
	private static final int BUTTON_X = 380;
	private static final int BUTTON_Y = 5;
	/** Our own sprite slot. Negative ids do not collide with the game's. */
	private static final int ICON_SPRITE_ID = -1701;

	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_X_STEP = 48;
	private static final int ITEM_Y_STEP = 36;
	private static final int ITEM_ROW_START = 51;

	/** Runaway-rebuild guard: more applies than this in a second and we stand down. */
	private static final int MAX_APPLIES_PER_SECOND = 20;

	private final Client client;
	private final ClientThread clientThread;
	private final HcimGuidePlugin plugin;
	private final WithdrawService withdrawService;

	private Widget button;
	private boolean active;
	private boolean buttonAdded;
	private boolean applying;
	private boolean iconRegistered;

	/** widget index -> {originalX, originalY, hidden} captured before filtering. */
	private final Map<Integer, int[]> savedLayout = new HashMap<>();
	private int savedScrollHeight;
	private String savedTitle;

	private long windowStart;
	private int appliesInWindow;

	@Inject
	private WithdrawBankFilter(Client client, ClientThread clientThread, HcimGuidePlugin plugin,
		WithdrawService withdrawService)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.plugin = plugin;
		this.withdrawService = withdrawService;
	}

	/** True when the filter is on and actually ours to apply. */
	public boolean isActive()
	{
		return active && !questHelperOwnsBank();
	}

	/** The bank finished building: make sure the button exists, then re-apply. */
	public void onBankBuilt()
	{
		savedLayout.clear();   // the rebuild replaced every widget

		if (!buttonAdded)
		{
			addButton();
		}
		else
		{
			refreshButton();
		}

		if (active && !questHelperOwnsBank())
		{
			applyFilter();
		}
	}

	/**
	 * The bank interface was (re)loaded, so any widget we added to it is gone.
	 * Forget it and let the next build create a single replacement.
	 */
	public void onBankInterfaceLoaded()
	{
		buttonAdded = false;
		button = null;
		savedLayout.clear();
		savedTitle = null;
	}

	public void reset()
	{
		active = false;
		buttonAdded = false;
		button = null;
		savedLayout.clear();
		savedTitle = null;
	}

	/**
	 * Whether Quest Helper's bank tab is showing. It renames the bank to
	 * "Tab &lt;col=...&gt;", which is the only signal available from outside that
	 * plugin. Both of us rewriting the same item widgets on every build is what
	 * locked the client up when its button was pressed.
	 */
	private boolean questHelperOwnsBank()
	{
		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		return title != null && title.getText() != null && title.getText().startsWith("Tab ");
	}

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
			button.setOpacity(active ? 0 : 100);
		}
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
	 * Hides bank items the step does not call for and packs the rest into a grid,
	 * so the bank shows only what you came for.
	 */
	private void applyFilter()
	{
		if (applying || overActive())
		{
			return;
		}

		Set<Integer> wanted = withdrawService.outstandingItemIds(plugin.getCurrentWithdrawItems());
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (container == null || bank == null)
		{
			return;
		}

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

				// Children past the item count are separators and tab furniture.
				boolean keep = i < items.length && wanted.contains(items[i].getId());
				if (!keep)
				{
					child.setHidden(true);
					continue;
				}

				child.setHidden(false);
				child.setOriginalX(ITEM_ROW_START + (shown % ITEMS_PER_ROW) * ITEM_X_STEP);
				child.setOriginalY((shown / ITEMS_PER_ROW) * ITEM_Y_STEP);
				child.revalidate();
				shown++;
			}

			int rows = (shown + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
			container.setScrollHeight(Math.max(rows * ITEM_Y_STEP, container.getHeight()));
			updateScrollbar();

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
	 * Resizes the scrollbar to match. Deferred rather than called inline: applying
	 * happens from the bank's own build script, and running a script from inside
	 * one trips "scripts are not reentrant" and takes the client down. Quest Helper
	 * defers this call for the same reason.
	 */
	private void updateScrollbar()
	{
		clientThread.invokeLater(() -> client.runScript(ScriptID.UPDATE_SCROLLBAR,
			InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, 0));
	}

	/**
	 * Stands the filter down if it is asked to apply implausibly often, which
	 * means something else is rebuilding the bank in response to us. Better a
	 * filter that switches itself off than a client that has to be killed.
	 */
	private boolean overActive()
	{
		long now = System.currentTimeMillis();
		if (now - windowStart > 1000)
		{
			windowStart = now;
			appliesInWindow = 0;
		}
		if (++appliesInWindow <= MAX_APPLIES_PER_SECOND)
		{
			return false;
		}
		log.warn("Bank filter re-applied {} times in a second; standing down", appliesInWindow);
		active = false;
		refreshButton();
		return true;
	}

	/**
	 * Puts back everything {@link #applyFilter()} changed, from state saved at
	 * apply time rather than by asking the game to rebuild the bank — that
	 * script's arguments are not something I could verify, and getting them wrong
	 * breaks the interface.
	 */
	private void restore()
	{
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
		updateScrollbar();

		Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (title != null && savedTitle != null)
		{
			title.setText(savedTitle);
			savedTitle = null;
		}
	}
}
