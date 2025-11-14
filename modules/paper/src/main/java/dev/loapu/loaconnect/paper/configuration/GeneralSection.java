package dev.loapu.loaconnect.paper.configuration;

import dev.loapu.loaconnect.paper.LoaConnectPlugin;

import java.net.URI;

public record GeneralSection(String baseUrl, int port, boolean behindReverseProxy, URI callbackUri, int loginTimeoutInMinutes, boolean syncGroups, String groupPrefix)
{
	public boolean valid()
	{
		try
		{
			//noinspection ResultOfMethodCallIgnored
			URI.create(baseUrl);
			if (loginTimeoutInMinutes <= 0 || loginTimeoutInMinutes > 60)
				throw new IllegalArgumentException("Invalid login timeout");
			return true;
		}
		catch (NullPointerException | IllegalArgumentException e)
		{
			LoaConnectPlugin.instance().getComponentLogger().error("The general settings section contains an error: ", e);
			return false;
		}
	}
}
