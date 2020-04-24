/* 
 * AdvancedBan-Converter, a basic data converter for AdvancedBan
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
import java.util.HashSet;
import java.util.Set;

import me.leoko.advancedban.MethodInterface;
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
	
    private final File folder;
	private final DatabaseManager db;
	private final PunishmentManager punishments;
	
	public AdvancedBanConverter(File folder, DatabaseManager db, PunishmentManager punishments) {
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
	
	void doConversion(MethodInterface mi) throws SQLException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, ClassNotFoundException {
		loadOldDb(mi);
		convert();
	}
	
	@Override
	public void close() throws SQLException {
		connection.close();
	}
	
	private boolean equals(Punishment p1, Punishment p2) {
		return p1.getType() == p2.getType() && p1.getReason() == p2.getReason() && p1.getStart() == p2.getStart() && p1.getEnd() == p2.getEnd();
	}
	
	private boolean anyContains(Set<Punishment> set, Punishment punishment) {
		for (Punishment check : set) {
			if (equals(check, punishment)) {
				return true;
			}
		}
		return false;
	}
	
	private void convert() throws SQLException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {		
		// retrieve from old db
		Set<Punishment> punishmentsOld = new HashSet<Punishment>();
		Set<Punishment> historyOld = new HashSet<Punishment>();
		try (ResultSet results1 = getFromOld(SQLQuery.SELECT_ALL_PUNISHMENTS);  ResultSet results2 = getFromOld(SQLQuery.SELECT_ALL_PUNISHMENTS_HISTORY)) {
			while (results1.next()) {
				punishmentsOld.add(punishments.getPunishmentFromResultSet(results1));
			}
			while (results2.next()) {
				historyOld.add(punishments.getPunishmentFromResultSet(results2));
			}
		}
		// get the id field once to avoid redundant reflection calls
		Field idField = Punishment.class.getDeclaredField("id");
		idField.setAccessible(true);
		// add to new db
		for (Punishment punishment : historyOld) {
			
			// check if the punishment is also an active punishment
			if (anyContains(punishmentsOld, punishment)) {
				addToPunishmentsAndHistory(idField, punishment, db); // if so, add the punishment to new history and new punishments
			} else {
				addToHistoryOnly(idField, punishment, db); // otherwise, add the punishment to new history only
			}
			
		}
	}
	
	private static void addToPunishmentsAndHistory(Field idField, Punishment punishment, DatabaseManager db) throws IllegalArgumentException, IllegalAccessException, SQLException {
		idField.set(punishment, -1);
		db.executeStatement(SQLQuery.INSERT_PUNISHMENT_HISTORY, punParams(punishment));
		db.executeStatement(SQLQuery.INSERT_PUNISHMENT, punParams(punishment));
		try (ResultSet rs = db.executeResultStatement(SQLQuery.SELECT_EXACT_PUNISHMENT, punishment.getUuid(), punishment.getStart(), punishment.getType().name())){
			if (rs.next()) {
				idField.set(punishment, rs.getInt("id"));
			} else {
				throw new IllegalStateException("Could not rewrite ID for punishment!");
			}
		}
	}
	
	private static void addToHistoryOnly(Field idField, Punishment punishment, DatabaseManager db) throws IllegalArgumentException, IllegalAccessException {
		idField.set(punishment, -1);
		db.executeStatement(SQLQuery.INSERT_PUNISHMENT_HISTORY, punParams(punishment));
	}
	
	private static Object[] punParams(Punishment punishment) {
		return new Object[] {punishment.getName(), punishment.getUuid(), punishment.getReason(), punishment.getOperator(), punishment.getType().name(), punishment.getStart(), punishment.getEnd(), punishment.getCalculation()};
	}
	
	private void loadOldDb(MethodInterface mi) throws SQLException, ClassNotFoundException {
        if (mysql()) {
            Class.forName("org.hsqldb.jdbc.JDBCDriver");
            connection = DriverManager.getConnection("jdbc:hsqldb:file:" + new File(folder.getParentFile(), "AdvancedBan") + "/data/storage;hsqldb.lock_file=false", "SA", "");
        } else {
            ip = mi.getString(mi.getMySQLFile(), "MySQL.IP", "Unknown");
            dbName = mi.getString(mi.getMySQLFile(), "MySQL.DB-Name", "Unknown");
            usrName = mi.getString(mi.getMySQLFile(), "MySQL.Username", "Unknown");
            password = mi.getString(mi.getMySQLFile(), "MySQL.Password", "Unknown");
            port = mi.getInteger(mi.getMySQLFile(), "MySQL.Port", 3306);
            connection = DriverManager.getConnection("jdbc:mysql://" + ip + ":" + port + "/" + dbName + "?verifyServerCertificate=false&useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=utf8", usrName, password);
        }
	}
	
	private ResultSet getFromOld(SQLQuery query, Object...parameters) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, SQLException {
		String sql = getAlt(query);
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int n = 1; n <= parameters.length; n++) {
				statement.setObject(n, parameters[n]);
			}
			return statement.executeQuery();
		}
	}
	
	private String getAlt(SQLQuery query) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		Field mysql = SQLQuery.class.getDeclaredField("mysql");
		Field hsqldb = SQLQuery.class.getDeclaredField("hsqldb");
		mysql.setAccessible(true);
		hsqldb.setAccessible(true);
		return (String) (mysql() ? hsqldb.get(query) : mysql.get(query));
	}
	
}
