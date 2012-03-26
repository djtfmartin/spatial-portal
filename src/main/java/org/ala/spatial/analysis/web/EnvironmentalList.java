package org.ala.spatial.analysis.web;

import au.org.emii.portal.composer.MapComposer;
import au.org.emii.portal.menu.MapLayer;
import au.org.emii.portal.util.LayerUtilities;
import java.util.ArrayList;
import java.util.Set;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.ala.spatial.util.CommonData;
import org.ala.spatial.util.ListEntry;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zul.Image;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listcell;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.ListitemRenderer;
import org.zkoss.zul.SimpleListModel;

/**
 *
 * @author ajay
 */
public class EnvironmentalList extends Listbox {

    ArrayList<ListEntry> listEntries;
    float[] threasholds = {0.1f, 0.3f, 1.0f};
    SimpleListModel listModel;
    MapComposer mapComposer;
    boolean includeAnalysisLayers;
    boolean disableContextualLayers;
    boolean singleDomain;

    public void init(MapComposer mc, boolean includeAnalysisLayers, boolean disableContextualLayers, boolean singleDomain) {
        mapComposer = mc;
        this.includeAnalysisLayers = includeAnalysisLayers;
        this.disableContextualLayers = disableContextualLayers;
        this.singleDomain = singleDomain;
        
        try {
            setupListEntries();

            setupList();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void setupListEntries() {
            listEntries = new ArrayList<ListEntry>();
            JSONArray ja = CommonData.getLayerListJSONArray();
            for(int i=0;i<ja.size();i++) {
                JSONObject jo = ja.getJSONObject(i);
                listEntries.add(
                        new ListEntry(jo.getString("name"),
                        (jo.containsKey("displayname") ? jo.getString("displayname") : jo.getString("name")),
                        (jo.containsKey("classification1") ? jo.getString("classification1") : ""),
                        (jo.containsKey("classification2") ? jo.getString("classification2") : ""),
                        (jo.containsKey("type") ? jo.getString("type") : ""),
                         (jo.containsKey("domain") ? jo.getString("domain") : ""),
                        jo));
        }

                if(includeAnalysisLayers) {         //add
                    for(MapLayer ml : mapComposer.getAnalysisLayers()) {
                        ListEntry le = null;
                        if(ml.getSubType() == LayerUtilities.ALOC) {
                            le = new ListEntry((String)ml.getData("pid"), ml.getDisplayName(), "Analysis", "Classification", "Contextual", null, null);
                        } else if(ml.getSubType() == LayerUtilities.MAXENT) {
                            le = new ListEntry((String)ml.getData("pid"), ml.getDisplayName(), "Analysis", "Prediction", "Environmental", null, null);
                        } else if(ml.getSubType() == LayerUtilities.GDM) {
                            le = new ListEntry((String)ml.getData("pid"), ml.getDisplayName(), "Analysis", "GDM", "Environmental", null, null);
                        } else if(ml.getSubType() == LayerUtilities.ODENSITY) {
                            le = new ListEntry((String)ml.getData("pid"), ml.getDisplayName(), "Analysis", "Occurrence Density", "Environmental" ,null, null);
                        } else if(ml.getSubType() == LayerUtilities.SRICHNESS) {
                            le = new ListEntry((String)ml.getData("pid"), ml.getDisplayName(), "Analysis", "Species Richness", "Environmental", null, null);
                        }
                        if(le != null) {
                            listEntries.add(le);
                        }
                    }
            }
    }

    public void setupList() {
        try {
                setItemRenderer(new ListitemRenderer() {

                    @Override
                    public void render(Listitem li, Object data) {
                        new Listcell(((ListEntry) data).catagoryNames()).setParent(li);
                        new Listcell(((ListEntry) data).displayname).setParent(li);

                        Listcell lc = new Listcell();
                        lc.setParent(li);
                        lc.setValue(((ListEntry) data));
                        
                        String type = ((ListEntry) data).type;
                        if (disableContextualLayers && type.equalsIgnoreCase("contextual")) {
                            li.setDisabled(true);
                        }   
                        
                        Image img = new Image();
                        img.setSrc("/img/information.png");

                        img.addEventListener("onClick", new EventListener() {

                            @Override
                            public void onEvent(Event event) throws Exception {
                                //re-toggle the checked flag (issue 572)
                                Listitem li = (Listitem) event.getTarget().getParent().getParent();
                                li.getListbox().toggleItemSelection(li);
                                EnvironmentalList el = (EnvironmentalList) li.getParent();
                                el.updateDistances();

                                String s = ((ListEntry) ((Listcell) event.getTarget().getParent()).getValue()).name;
                                String metadata = CommonData.layersServer + "/layer/" + s;
                                mapComposer.activateLink(metadata, "Metadata", false);

                            }
                        });
                        img.setParent(lc);
                        
                        //String type = ((ListEntry) data).type;
                            
                        if (type.equalsIgnoreCase("environmental")){
                            float value = ((ListEntry) data).value;
                            lc = new Listcell(" ");
                            if (threasholds[0] > value) {
                                lc.setSclass("lcRed");//setStyle("background: #bb2222;");
                            } else if (threasholds[1] > value) {
                                lc.setSclass("lcYellow");//lc.setStyle("background: #ffff22;");
                            } else if (1 >= value) {
                                lc.setSclass("lcGreen");//lc.setStyle("background: #22aa22;");
                            } else {
                                lc.setSclass("lcWhite");//setStyle("background: #ffffff;");
                            }
                            lc.setParent(li);
                        }                        
                    };
                });

                listModel = new SimpleListModel(listEntries);
                setModel(listModel);

                renderAll();
            
        } catch (Exception e) {
            System.out.println("error setting up env list");
            e.printStackTrace(System.out);
        }
    }

    @Override
    public void toggleItemSelection(Listitem item) {
        super.toggleItemSelection(item);
        //update minimum distances here
    }

    public void updateDistances() {
        if(listEntries == null) {
            return;
        }

        for (ListEntry le : listEntries) {
            le.value = 2;
        }

        String fieldId;
        for (Object o : getSelectedItems()) {
            ListEntry l = listEntries.get(((Listitem) o).getIndex());
            if(l.type.equalsIgnoreCase("environmental")
                    && l.layerObject != null && l.layerObject.get("fields") != null
                    && (fieldId = getFieldId(l.layerObject)) != null
                    && CommonData.getDistancesMap().get(fieldId) != null) {
                for (ListEntry le : listEntries) {
                    if(le.layerObject != null && le.layerObject.get("fields") != null) {
                        String fieldId2 = getFieldId(le.layerObject);

                        Double d = CommonData.getDistancesMap().get(fieldId).get(fieldId2);
                        if(d != null) {
                            le.value = (float)Math.min(le.value, d.doubleValue());
                        }
                    }
                }
            }
        }

        for (int i = 0; i < listEntries.size(); i++) {
            float value = listEntries.get(i).value;
            String type = listEntries.get(i).type;
            Listcell lc = (Listcell) (getItemAtIndex(i).getLastChild());
            if (type.equalsIgnoreCase("environmental")){
                if (!getSelectedItems().isEmpty() && threasholds[0] > value) {
                    lc.setSclass("lcRed");//setStyle("background: #bb2222;");
                } else if (!getSelectedItems().isEmpty() && threasholds[1] > value) {
                    lc.setSclass("lcYellow");//lc.setStyle("background: #ffff22;");
                } else if (getSelectedItems().isEmpty() || 1 >= value) {
                    lc.setSclass("lcGreen");//lc.setStyle("background: #22aa22;");
                } else {
                    lc.setSclass("lcWhite");//lc.setStyle("background: #ffffff;");
                }
            }
        }
    }

    String getFieldId(JSONObject layerObject) {
        String fieldId = null;
        try {
            JSONArray ja = (JSONArray)layerObject.get("fields");
            for(int i=0;i<ja.size();i++) {
                JSONObject jo = (JSONObject)ja.get(i);
                if(true) { //jo.getString("analysis").equalsIgnoreCase("true")) {
                    fieldId = jo.getString("id");
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fieldId;
    }

    public void onSelect(Event event) {
        updateDistances();
    }

    public String[] getSelectedLayers() {
        Set selectedItems = getSelectedItems();
        String[] selected = new String[selectedItems.size()];
        int i = 0;
        System.out.print("getSelectedLayers: ");
        for (Object o : selectedItems) {
            selected[i] = listEntries.get(((Listitem) o).getIndex()).name;
            i++;
            System.out.print(listEntries.get(((Listitem) o).getIndex()).displayname + ", " + listEntries.get(((Listitem) o).getIndex()).name);
        }
        System.out.println("");
        return selected;
    }

    void selectLayers(String[] layers) {
        //HashSet<Listitem> items = new HashSet<Listitem>();
        for (int i = 0; i < listEntries.size(); i++) {
            for (int j = 0; j < layers.length; j++) {
                if (listEntries.get(i).displayname.equalsIgnoreCase(layers[j])
                        || listEntries.get(i).name.equalsIgnoreCase(layers[j])) {
//                    items.add(getItemAtIndex(i));
                    if(!getItemAtIndex(i).isSelected()) {
                        toggleItemSelection(getItemAtIndex(i));
                    }
                    break;
                }
            }
        }
//        if (items.size() > 0) {
//            setSelectedItems(items);
//        }
        updateDistances();
    }

    @Override
    public void clearSelection() {
        updateDistances();
        super.clearSelection();        
    }

    public boolean getIncludeAnalysisLayers() {
        return includeAnalysisLayers;
    }
}
