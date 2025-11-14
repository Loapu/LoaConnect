package dev.loapu.loaconnect.paper.transactions;

import com.nimbusds.oauth2.sdk.id.State;
import com.sun.net.httpserver.HttpExchange;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.codehaus.plexus.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Transaction
{
	private static final String PREFIX = "T-";
	private static final String SPACER = " | ";
	private static final List<NamedTextColor> colors = new LinkedList<>(Arrays.asList(
		NamedTextColor.BLUE,
		NamedTextColor.DARK_BLUE,
		NamedTextColor.RED,
		NamedTextColor.DARK_RED,
		NamedTextColor.YELLOW,
		NamedTextColor.GOLD,
		NamedTextColor.GREEN,
		NamedTextColor.DARK_GREEN,
		NamedTextColor.GRAY,
		NamedTextColor.DARK_GRAY,
		NamedTextColor.LIGHT_PURPLE,
		NamedTextColor.DARK_PURPLE,
		NamedTextColor.AQUA,
		NamedTextColor.DARK_AQUA
	));
	private final NamedTextColor transactionColor;
	
	private static final Map<State, Transaction> stateTransactions = new ConcurrentHashMap<>();
	private static final Map<String, State> transactionStates = new ConcurrentHashMap<>();
	
	private final String transactionId;
	private final ComponentLogger logger;
	private final long startTime;
	
	public Transaction()
	{
		transactionId = generateTransactionId();
		transactionColor = pickColor();
		logger = LoaConnectPlugin.instance().getComponentLogger();
		info("===== TRANSACTION STARTED =====");
		startTime = System.currentTimeMillis();
	}
	
	private NamedTextColor pickColor()
	{
		if (colors.isEmpty()) return NamedTextColor.WHITE;
		int index = new SecureRandom().nextInt(colors.size());
		return colors.remove(index);
	}
	
	private String generateTransactionId()
	{
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder(6);
		for (int i = 0; i < 6; i++)
		{
			int hex = random.nextInt(16);
			sb.append(Integer.toHexString(hex));
		}
		return PREFIX + sb.toString().toUpperCase();
	}
	
	private String messagePrefix()
	{
		return transactionId + SPACER;
	}
	
	public String transactionId()
	{
		return transactionId;
	}
	
	private Component component(String message)
	{
		return Component.text().append(Component.text(messagePrefix(), transactionColor)).append(Component.text(message)).build();
	}
	
	private void sendResponse(HttpExchange exchange, String message)
	{
		message = messagePrefix() + message;
		try
		{
			exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
			byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			OutputStream os = exchange.getResponseBody();
			os.write(bytes);
			os.close();
		}
		catch (IOException e)
		{
			error("Error sending response", e);
		}
		
	}
	
	public void info(String message, HttpExchange exchange)
	{
		info(message);
		sendResponse(exchange, message);
	}
	
	public void info(String message)
	{
		logger.info(component(message));
	}
	
	public void info(String message, Object... args)
	{
		logger.info(component(message), args);
	}
	
	public void warn(String message, HttpExchange exchange)
	{
		warn(message);
		sendResponse(exchange, message);
	}
	
	public void warn(String message)
	{
		logger.warn(component(message));
	}
	
	public void warn(String message, Object... args)
	{
		logger.warn(component(message), args);
	}
	
	public void error(String message, HttpExchange exchange)
	{
		error(message);
		sendResponse(exchange, message);
	}
	
	public void error(String message)
	{
		logger.error(component(message));
	}
	
	public void error(String message, Object... args)
	{
		logger.error(component(message), args);
	}
	
	public void error(String message, Throwable t)
	{
		logger.error(component(message), t);
	}
	
	public void linkState(State state)
	{
		stateTransactions.put(state, this);
		transactionStates.put(transactionId, state);
	}
	
	public void end()
	{
		long endTime = System.currentTimeMillis();
		long duration = endTime - startTime;
		info("===== TRANSACTION ENDED (Took {} ms) =====", duration);
		if (transactionColor != NamedTextColor.WHITE) colors.add(transactionColor);
		State state = transactionStates.remove(transactionId);
		if (state == null) return;
		stateTransactions.remove(state);
	}
	
	public static Transaction byState(State state)
	{
		return stateTransactions.get(state);
	}
}
