package dev.loapu.loaconnect.paper.configuration;

import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;

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
		catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}
}
