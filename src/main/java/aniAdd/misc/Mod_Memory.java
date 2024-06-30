/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aniAdd.misc;

import aniAdd.IAniAdd;
import aniAdd.Modules.BaseModule;
import aniAdd.config.AniConfiguration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * @author Arokh
 */
public class Mod_Memory extends BaseModule {
    TreeMap<String, Object> mem;

    public Mod_Memory() {
        load();
    }

    public Mod_Memory(AniConfiguration configuration) {
        loadFromConfiguration(configuration);
    }

    public void put(String key, Object value) {
        Logger.getGlobal().log(Level.WARNING, "Setting " + key + " changed to " + value.toString());
        mem.put(key, value);
    }

    public Object get(String key) {
        return mem.get(key);
    }

    public Object get(String key, Object defVal) {
        if (mem.containsKey(key)) {
            return mem.get(key);
        } else {
            mem.put(key, defVal);
            return defVal;
        }
    }

    public void remove(String key) {
        mem.remove(key);
    }

    public int size() {
        return mem.size();
    }


    public void save() {
        Preferences prefs = Preferences.userNodeForPackage(Mod_Memory.class);

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(mem);
            out.close();

            prefs.putByteArray("AniAdd", bos.toByteArray());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void loadFromConfiguration(AniConfiguration configuration) {
        load();
        mem.putAll(configuration.ToMod_Memory());
    }

    public void load() {
        mem = new TreeMap<String, Object>();
        try {
            ObjectInputStream in = null;
            Preferences prefs = Preferences.userNodeForPackage(Mod_Memory.class);
            byte[] b = prefs.getByteArray("AniAdd", null);

            if (b != null) {
                ByteArrayInputStream bis = new ByteArrayInputStream(b);
                in = new ObjectInputStream(bis);
                mem = (TreeMap<String, Object>) in.readObject();
                in.close();
//                StringBuilder builder = new StringBuilder("Mod_Memory Settings\n");
//                for (String key : mem.keySet()) {
//                    builder.append(key).append(" === ");
//                    Object value = mem.get(key);
//                    builder.append(value == null ? "null" : mem.get(key).toString()).append("-------------\n");
//                }
//                Logger.getGlobal().log(Level.WARNING, builder.toString());
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void clear() {
        mem.clear();
        ComFire(new CommunicationEvent(this, CommunicationEvent.EventType.Information, "SettingsCleared"));
    }

    public String toString() {
        String settings = "";
        for (Entry<String, Object> entry : mem.entrySet()) {
            settings += entry.getKey() + " = " + (entry.getValue() != null ? entry.getValue().toString().replace("\n", "\\n") : "[null]") + "\n";
        }

        return settings;
    }


    // <editor-fold defaultstate="collapsed" desc="IModule">
    protected String modName = "Memory";
    protected eModState modState = eModState.New;

    public eModState ModState() {
        return modState;
    }

    public String ModuleName() {
        return modName;
    }

    public void Initialize(IAniAdd aniAdd) {
        modState = eModState.Initializing;
        aniAdd.addComListener(comEvent -> {});
        modState = eModState.Initialized;
    }

    public void Terminate() {
        modState = eModState.Terminating;
        save();
        modState = eModState.Terminated;
    }
    // </editor-fold>
}
