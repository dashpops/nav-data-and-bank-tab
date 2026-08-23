package com.conde.hcimguide.model;

import java.util.Objects;

/**
 * One rendered line of a "Withdraw:" step for the sidebar: the label to show and
 * where the item currently is. Precomputed on the client thread so the Swing
 * panel can paint it without touching the game API.
 */
public class WithdrawLine
{
	private final String label;
	private final ItemState state;

	public WithdrawLine(String label, ItemState state)
	{
		this.label = label;
		this.state = state;
	}

	public String getLabel()
	{
		return label;
	}

	public ItemState getState()
	{
		return state;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof WithdrawLine))
		{
			return false;
		}
		WithdrawLine other = (WithdrawLine) o;
		return state == other.state && Objects.equals(label, other.label);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(label, state);
	}
}
