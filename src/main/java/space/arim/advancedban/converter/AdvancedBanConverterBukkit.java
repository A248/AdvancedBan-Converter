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

import org.bukkit.plugin.java.JavaPlugin;

import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.PunishmentManager;

public class AdvancedBanConverterBukkit extends JavaPlugin {

	@Override
	public void onEnable() {
		try (AdvancedBanConverter converter = new AdvancedBanConverter(getDataFolder(), DatabaseManager.get(), PunishmentManager.get())) {
			converter.doConversion(Universal.get().getMethods());
		} catch (Exception ex) {
			getLogger().info("Error encountered:");
			ex.printStackTrace();
		}
		getLogger().info("Completed all actions.");
		getLogger().warning("----");
		getLogger().warning("You should now remove AdvancedBan-Converter if it was successful.");
		getLogger().warning("----");
	}
	
}
