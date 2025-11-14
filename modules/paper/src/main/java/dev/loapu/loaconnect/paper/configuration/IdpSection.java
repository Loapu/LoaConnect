package dev.loapu.loaconnect.paper.configuration;

import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;

public record IdpSection(String name, Issuer issuer, ClientID clientId, Secret clientSecret,
						 Scope scope)
{
	public boolean valid()
	{
		try
		{
			if (name.length() > 64) throw new IllegalArgumentException("idp name length exceed 64");
			if (!issuer.isValid()) throw new IllegalArgumentException("idp issuer is invalid");
			return true;
		}
		catch (IllegalArgumentException e)
		{
			LoaConnectPlugin.instance().getComponentLogger().error("The idp settings section contains an error: ", e);
			return false;
		}
	}
}
