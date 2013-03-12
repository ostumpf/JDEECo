package cz.cuni.mff.d3s.deeco.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Simple wrapper of java.util.logging.Logger with singleton object and
 * thread-safe methods
 * 
 * @author Ilias Gerostathopoulos
 * 
 */
public class StandardLogger implements ILogger {

	private static ILogger instance;
	private Logger logger;

	private StandardLogger() {
		logger = Logger.getLogger(this.getClass().getPackage().getName());
		InputStream inputStream = getClass().getClassLoader()
				.getResourceAsStream("logging.properties");
		try {
			LogManager.getLogManager().readConfiguration(inputStream);
		} catch (IOException e) {
			Logger.getAnonymousLogger().severe(
					"Could not load custom logging.properties file");
			Logger.getAnonymousLogger().severe(e.getMessage());
		}
	}

	public static ILogger getLogger() {
		if (instance == null) {
			synchronized (StandardLogger.class) {
				if (instance == null) {
					instance = new StandardLogger();
				}
			}
		}
		return instance;
	}

	public synchronized void fine(String s) {
		logger.fine(s);
	}
	
	public synchronized void fine(String s, Throwable t) {
		logger.log(Level.FINE,s,t);
	}

	public synchronized void info(String s) {
		logger.info(s);
	}
	
	public synchronized void info(String s, Throwable t) {
		logger.log(Level.INFO,s,t);
	}
	
	public synchronized void severe(String s) {
		logger.severe(s);
	}
	
	public synchronized void severe(String s, Throwable t) {
		logger.log(Level.SEVERE,s,t);
	}

}
