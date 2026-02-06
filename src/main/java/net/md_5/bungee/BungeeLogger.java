package net.md_5.bungee;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Logger to handle formatting and storage of the proxy's logger.
 */
public class BungeeLogger extends Logger {

    static final BungeeLogger instance = new BungeeLogger();

    public BungeeLogger() {
        super("BungeeCord", null);
        setUseParentHandlers(false); // BMC
        try {
            // BMC start - different date formats, infinite log file size
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new ConsoleLogFormatter(new SimpleDateFormat("HH:mm:ss")));
            FileHandler fileHandler = new FileHandler("proxy.log", true);
            fileHandler.setFormatter(new ConsoleLogFormatter(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")));
            addHandler(consoleHandler);
            addHandler(fileHandler);
            // BMC end
        } catch (IOException ex) {
            System.err.println("Could not register logger!");
            ex.printStackTrace();
        }
    }

    /*@Override // BMC - comment out
    public void log(LogRecord record) {
        super.log(record);
        if (record.getLevel() == Level.SEVERE || record.getLevel() == Level.WARNING) {
            System.err.print(record.getMessage());
        } else {
            System.out.print(record.getMessage());
        }
    }*/

    private static class ConsoleLogFormatter extends Formatter {

        // BMC start - supply date format
        private SimpleDateFormat dateFormat;

        private ConsoleLogFormatter(SimpleDateFormat dateFormat) {
            this.dateFormat = dateFormat;
        }
        // BMC end

        @Override
        public String format(LogRecord logrecord) {
            StringBuilder formatted = new StringBuilder();

            formatted.append(dateFormat.format(logrecord.getMillis()));
            Level level = logrecord.getLevel();

            if (level == Level.FINEST) {
                formatted.append(" [FINEST] ");
            } else if (level == Level.FINER) {
                formatted.append(" [FINER] ");
            } else if (level == Level.FINE) {
                formatted.append(" [FINE] ");
            } else if (level == Level.INFO) {
                formatted.append(" [INFO] ");
            } else if (level == Level.WARNING) {
                formatted.append(" [WARNING] ");
            } else if (level == Level.SEVERE) {
                formatted.append(" [SEVERE] ");
            }

            formatted.append(formatMessage(logrecord));
            formatted.append('\n');
            Throwable throwable = logrecord.getThrown();

            if (throwable != null) {
                StringWriter writer = new StringWriter();

                throwable.printStackTrace(new PrintWriter(writer));
                formatted.append(writer);
            }

            return formatted.toString();
        }
    }
}
