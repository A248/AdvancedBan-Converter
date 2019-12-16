/* 
 * AdvancedBan-Converter
 * Copyright © 2019 Anand Beh <https://www.arim.space>
 * 
 * AdvancedBan-Converter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * AdvancedBan-Converter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with AdvancedBan-Converter. If not, see <https://www.gnu.org/licenses/>
 * and navigate to version 3 of the GNU General Public License.
 */
package space.arim.advancedban.converter;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.SQLQuery;

public class AdvancedBanConverter implements AutoCloseable {

	static final String ERROR_MSG = "Sorry, but an error was encountered during conversions. Please contact @A248 on Github or A248#5445 Discord.";
	
    private String ip;
    private String dbName;
    private String usrName;
    private String password;
    private int port = 3306;
    private Connection connection;
	
    private final Logger logger;
    private final File folder;
	private final DatabaseManager db;
	private final PunishmentManager punishments;
	
	public AdvancedBanConverter(Logger logger, File folder, DatabaseManager db, PunishmentManager punishments) {
		this.logger = logger;
		this.folder = folder;
		this.db = db;
		this.punishments = punishments;
	}
	
	private boolean mysql() {
		return db.isUseMySQL();
	}
	
	String toMode() {
		return mysql() ? "MySQL (external)" : "HSQLDB (local)"; 
	}
	
	String fromMode() {
		return !mysql() ? "MySQL (external)" : "HSQLDB (local)"; 
	}
	
	public void doConversion() {
		loadOldDb();
		try {
			convert();
		} catch (SQLException ex) {
			error("Old database retrieval failed", ex);
		}
	}
	
	@Override
	public void close() throws SQLException {
		connection.close();
	}
	
	private void convert() throws SQLException {		
		// retrieve existing
		HashMap<Integer, Punishment> punishmentNew = new HashMap<Integer, Punishment>();
		HashMap<Integer, Punishment> historyNew = new HashMap<Integer, Punishment>();
		punishments.getPunishments(SQLQuery.SELECT_ALL_PUNISHMENTS).forEach((punishment) -> {
			punishmentNew.put(punishment.getId(), punishment);
		});
		punishments.getPunishments(SQLQuery.SELECT_ALL_PUNISHMENTS_HISTORY).forEach((punishment) -> {
			historyNew.put(punishment.getId(), punishment);
		});
		// retrieve from old db
		Set<Punishment> punishmentsOld = new HashSet<Punishment>();
		try (ResultSet results1 = getFromOld(SQLQuery.SELECT_ALL_PUNISHMENTS)) {
			while (results1.next()) {
				punishmentsOld.add(punishments.getPunishmentFromResultSet(results1));
			}
		}
		Set<Punishment> historyOld = new HashSet<Punishment>();
		try (ResultSet results2 = getFromOld(SQLQuery.SELECT_ALL_PUNISHMENTS_HISTORY)) {
			while (results2.next()) {
				historyOld.add(this.punishments.getPunishmentFromResultSet(results2));
			}
		}
		punishmentsOld.forEach((punishment) -> {
			if (punishmentNew.containsKey(punishment.getId())) {
				rewriteIdThenAddPunishment(punishment);
			} else {
				addPunishment(punishment);
			}
		});
		historyOld.forEach((punishment) -> {
			addPunishmentHistory(punishment);
		});
	}
	
	private Object[] punParams(Punishment punishment) {
		return new Object[] {punishment.getName(), punishment.getUuid(), punishment.getReason(), punishment.getOperator(), punishment.getType().name(), punishment.getStart(), punishment.getEnd(), punishment.getCalculation()};
	}
	
	private void addPunishment(Punishment punishment) {
		db.executeStatement(SQLQuery.INSERT_PUNISHMENT, punParams(punishment));
	}
	
	private void addPunishmentHistory(Punishment punishment) {
		db.executeStatement(SQLQuery.INSERT_PUNISHMENT_HISTORY, punParams(punishment));
	}
	
	private void rewriteIdThenAddPunishment(Punishment punishment) {
		try {
			Field idField = Punishment.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(punishment, -1);
			db.executeStatement(SQLQuery.INSERT_PUNISHMENT, punParams(punishment));
			try (ResultSet rs = db.executeResultStatement(SQLQuery.SELECT_EXACT_PUNISHMENT, punishment.getUuid(), punishment.getStart(), punishment.getType().name())){
				if (!rs.next()) {
					error("Could not rewrite id for " + punishment);
				}
			}
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException | SQLException ex) {
			error("Could not rewrite id for " + punishment, ex);
		}
	}
	
	private void error(String message) {
		logger.log(Level.WARNING, ERROR_MSG + " " + message);
	}
	
	private void error(String message, Throwable cause) {
		logger.log(Level.WARNING, ERROR_MSG + " " + message, cause);
	}
	
	private void loadOldDb() {
        if (mysql()) {
            try {
            	Class.forName("org.hsqldb.jdbc.JDBCDriver");
            } catch (ClassNotFoundException ex) {
                error("§cERROR: failed to load HSQLDB JDBC driver.", ex);
                return;
            }
            try {
                connection = DriverManager.getConnection("jdbc:hsqldb:file:" + new File(folder, "AdvancedBan") + "/data/storage;hsqldb.lock_file=false", "SA", "");
            } catch (SQLException ex) {
                error(" \n"
                        + " HSQLDB-Error\n"
                        + " Could not connect to HSQLDB-Server!\n"
                        + " Disabling plugin!\n"
                        + " Skype: Leoko33\n"
                        + " Issue tracker: https://github.com/DevLeoko/AdvancedBan/issues\n"
                        + " \n",
                        ex
                );
            }
        } else {
        	MethodInterface mi = Universal.get().getMethods();
            ip = mi.getString(mi.getMySQLFile(), "MySQL.IP", "Unknown");
            dbName = mi.getString(mi.getMySQLFile(), "MySQL.DB-Name", "Unknown");
            usrName = mi.getString(mi.getMySQLFile(), "MySQL.Username", "Unknown");
            password = mi.getString(mi.getMySQLFile(), "MySQL.Password", "Unknown");
            port = mi.getInteger(mi.getMySQLFile(), "MySQL.Port", 3306);
            try {
                connection = DriverManager.getConnection("jdbc:mysql://" + ip + ":" + port + "/" + dbName + "?verifyServerCertificate=false&useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=utf8", usrName, password);
            } catch (SQLException exc) {
               error(
                        " \n"
                        + " MySQL-Error\n"
                        + " Could not connect to MySQL-Server!\n"
                        + " Disabling plugin!\n"
                        + " Check your MySQL.yml\n"
                        + " Skype: Leoko33\n"
                        + " Issue tracker: https://github.com/DevLeoko/AdvancedBan/issues \n"
                        + " \n",
                        exc
                );
            }
        }
	}
	
	private ResultSet getFromOld(SQLQuery query, Object...parameters) {
		String sql = getAlt(query);
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int n = 0; n < parameters.length; n++) {
				statement.setObject(n, parameters[n]);
			}
			return statement.executeQuery();
		} catch (SQLException ex) {}
		return null;
	}
	
	private String getAlt(SQLQuery query) {
		try {
			Field mysql = SQLQuery.class.getDeclaredField("mysql");
			Field hsqldb = SQLQuery.class.getDeclaredField("hsqldb");
			mysql.setAccessible(true);
			hsqldb.setAccessible(true);
			return (String) (mysql() ? hsqldb.get(query) : mysql.get(query));
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
}
