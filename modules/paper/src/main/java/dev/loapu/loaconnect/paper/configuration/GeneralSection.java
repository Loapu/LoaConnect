package dev.loapu.loaconnect.paper.configuration;

import java.net.URI;

public record GeneralSection(String baseUrl, int port, boolean behindReverseProxy, String callbackEndpoint, URI callbackUri, int loginTimeoutInMinutes)
{
	public boolean valid()
	{
		try
		{
			URI.create(baseUrl);
			if (loginTimeoutInMinutes <= 0 || loginTimeoutInMinutes > 60) throw new RuntimeException("Invalid login timeout");
			return  true;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}
}
