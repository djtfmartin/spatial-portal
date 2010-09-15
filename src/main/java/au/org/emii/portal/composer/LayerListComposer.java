package au.org.emii.portal.composer;

import au.org.emii.portal.config.xmlbeans.Supplementary;
import au.org.emii.portal.settings.Settings;
import au.org.emii.portal.settings.SettingsSupplementary;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.zkoss.zhtml.Messagebox;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.SimpleTreeModel;
import org.zkoss.zul.SimpleTreeNode;
import org.zkoss.zul.Tree;
import org.zkoss.zul.Treecell;
import org.zkoss.zul.Treeitem;
import org.zkoss.zul.TreeitemRenderer;
import org.zkoss.zul.Treerow;

/**
 *
 * @author ajay
 */
public class LayerListComposer extends UtilityComposer {

    private Tree tree;
    private ArrayList empty = new ArrayList();
    private MapComposer mc;

    private final String satServer = "http://spatial-dev.ala.org.au"; // "http://localhost:8080"

    SettingsSupplementary settingsSupplementary;

    @Override
    public void afterCompose() {
        super.afterCompose();

        if (tree == null) {
            System.out.println("tree is null");
        } else {
            System.out.println("tree is ready");
        }

        mc = getThisMapComposer();

        System.out.print("Settings.Suppl:");
        SettingsSupplementary settingsSupplementary = mc.getSettingsSupplementary();
        if (settingsSupplementary == null) System.out.println(" is bad");
        else System.out.println(" is good");
        System.out.print("mc.geoserver:" + mc.geoServer);

        System.out.print("Settings.Global:");
        Settings settings = mc.getSettings();
        if (settingsSupplementary == null) System.out.println(" is bad");
        else System.out.println(" is good");


        System.out.println("Loading the LayerListComposer");
        iterateAndLoad2();


        //System.out.println("with:\n" + layerlist);

    }
    private MapComposer getThisMapComposer() {

        MapComposer mapComposer = null;
        Page page = getPage();
        mapComposer = (MapComposer) page.getFellow("mapPortalPage");

        return mapComposer;
    }


    public void iterateAndLoad2() {
        try {

            String layersListURL = "http://spatial-dev.ala.org.au" + "/alaspatial/ws/layers/list";
            HttpClient client = new HttpClient();
            GetMethod get = new GetMethod(layersListURL);
            //get.addRequestHeader("Content-type", "application/json");
            get.addRequestHeader("Accept", "application/json, text/javascript, */*");

            int result = client.executeMethod(get);
            String llist = get.getResponseBodyAsString();

            //String layerlist = (String)Sessions.getCurrent().getAttribute("layerlist");

            //Object llist = Sessions.getCurrent().getAttribute("layerlist");

            if (llist == null) {
                return;
            }

            ArrayList top = new ArrayList();

            TreeMap htCat1 = new TreeMap();
            TreeMap htCat2 = new TreeMap();
            System.out.println("LAYERLIST>>>>>>>>>>" + (String) llist);

            JSONArray layerlist = JSONArray.fromObject(llist);
            for (int i = 0; i < layerlist.size(); i++) {
                JSONObject jo = layerlist.getJSONObject(i);

                if (!jo.getBoolean("enabled")) {
                    continue;
                }

                System.out.println("TYPE:"+jo.getString("type"));

                List classNodes = new ArrayList();
                if (jo.getString("type").equalsIgnoreCase("Contextual")) {
                    classNodes = getContextualClasses(jo);
                }
                SimpleTreeNode stn;
                if (classNodes.isEmpty()) {
                     stn = new SimpleTreeNode(jo, empty);
                }
                else {
                    stn = new SimpleTreeNode(jo, classNodes);
                }
                addToMap2(htCat1, htCat2, jo.getString("classification1"), jo.getString("classification2"), stn);

            }

            System.out.println("ht1.size: " + htCat1.size());
            System.out.println("ht2.size: " + htCat2.size());

            Iterator it1 = htCat1.keySet().iterator();
            while (it1.hasNext()) {
                String catKey = (String) it1.next();
                JSONObject joCat = JSONObject.fromObject("{displayname:'" + catKey + "',type:'node'}");
                SimpleTreeNode cat = new SimpleTreeNode(joCat, (ArrayList) htCat1.get(catKey));
                top.add(cat);
            }


            SimpleTreeNode root = new SimpleTreeNode("ROOT", top);
            SimpleTreeModel stm = new SimpleTreeModel(root);
            tree.setModel(stm);

            renderTree();
        } catch (Exception e) {
            //FIXME:
        }

    }

    private List getContextualClasses(JSONObject joLayer) {
        String layerName = joLayer.getString("name");
        String layerDisplayName = joLayer.getString("displayname");
        String classesURL = "http://spatial-dev.ala.org.au" + "/geoserver/rest/gazetteer/" + layerName + ".json";
        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod(classesURL);
        //get.addRequestHeader("Content-type", "application/json");
        //get.addRequestHeader("Accept", "application/json, text/javascript, */*");
        List<String> classList = new ArrayList();
        List classNodes = new ArrayList();
        try {
            int result = client.executeMethod(get);
            String classes = get.getResponseBodyAsString();

            //JSONObject joClasses = JSONObject.fromObject(classes);
            System.out.println("CLASSES JSON:" + classes);
            classes = classes.replace("\"","").replace("{","").replace("}", "");

            String classAttribute = classes.split(":")[0];
            classList = Arrays.asList(classes.split(":")[1].split(","));
           // System.out.println("KEY:" + classAttribute);
           // classList = Arrays.asList((jo.getString(classAttribute)).split(","));
            
            for (String classVal : classList) {
                        System.out.println("CLASS:"+(String)classVal);
                        if (!classVal.contentEquals("none")) {
                            String info = "{displayname:'"
                                     + classVal
                                     + "',type:'class',displaypath:'"
                                     + joLayer.getString("displaypath")
                                     + "',uid:'"
                                     + joLayer.getString("uid")
                                     + "',classname:'"
                                     + classAttribute
                                     + "',layername:'"
                                     + layerDisplayName
                                     + "'}";
                            System.out.println(info);
                            JSONObject joClass = JSONObject.fromObject(info);
                            classNodes.add(new SimpleTreeNode(joClass,empty));
                        }
                    }
            return classNodes;
        } catch (Exception e) {
            System.out.println("Failure to get contextual classes.");
            return classNodes;
        }
    }

    private void addToMap2(TreeMap htCat1, TreeMap htCat2, String cat1, String cat2, SimpleTreeNode treeNode) {

        if (cat1.trim().equals("") || cat1.trim().equals("?")) {
            cat1 = "Other";
        }
        if (cat2.trim().equals("") || cat2.trim().equals("?")) {
            //cat2 = "Other";
            System.out.println("Adding layer to cat1.other as cat2=" + cat2);
            ArrayList alCat1 = (ArrayList) htCat1.get(cat1);
            if (alCat1 == null) {
                alCat1 = new ArrayList();
            }
            alCat1.add(treeNode);
            htCat1.put(cat1, alCat1);

        } else {

            // first check if cat1 already exists
            // if yes, grab the cat2 list and add add to its AL
            // else, create a new one and add it to cat1.list

            String cat2_full = cat1 + ">" + cat2;
            ArrayList alCat2 = (ArrayList) htCat2.get(cat2_full);
            if (alCat2 == null) {
                alCat2 = new ArrayList();
            }
            System.out.println("add new stn to: " + cat1 + " > " + cat2);
            alCat2.add(treeNode);
            if (!htCat2.containsKey(cat2_full)) {
                htCat2.put(cat2_full, alCat2);
            }

            ArrayList alCat1 = (ArrayList) htCat1.get(cat1);
            if (alCat1 == null) {
                alCat1 = new ArrayList();
            }
            System.out.println("\tAdding new cat2");
            JSONObject joCat2 = JSONObject.fromObject("{displayname:'" + cat2_full + "',type:'node'}");
            SimpleTreeNode stnCat2 = new SimpleTreeNode(joCat2, alCat2);
            System.out.println("\tadding cat2.stn (" + cat2 + ") to " + cat1 + " :: " + alCat1.contains(stnCat2) + " ::: " + alCat1.indexOf(stnCat2));
            System.out.println("\t=======================" + stnCat2);
            boolean found = false;
            for (int i=0; i<alCat1.size(); i++) {
                System.out.print("\t\t " + alCat1.get(i));
                if (stnCat2.toString().equals(alCat1.get(i).toString())) {
                    System.out.println(": found");
                    found = true;
                    break;
                } else {
                    System.out.println(": not this");
                }
            }
            if (!found) {
                alCat1.add(stnCat2);
            } 
            System.out.println("\t=======================");
            
            if (!htCat1.containsKey(cat1)) {
                htCat1.put(cat1, alCat1);
            } else {
                System.out.println("\thad existing");
            }

        }

    }

    private void renderTree() {
        tree.setTreeitemRenderer(new TreeitemRenderer() {

            @Override
            public void render(Treeitem item, Object data) throws Exception {

                SimpleTreeNode t = (SimpleTreeNode) data;

                JSONObject joLayer = JSONObject.fromObject(t.getData());

                Treerow tr = null;
                /*
                 * Since only one treerow is allowed, if treerow is not null,
                 * append treecells to it. If treerow is null, construct a new
                 * treerow and attach it to item.
                 */
                if (item.getTreerow() == null) {
                    tr = new Treerow();
                    tr.setParent(item);
                } else {
                    tr = item.getTreerow();
                    tr.getChildren().clear();
                }

                Treecell tcAdd = new Treecell();
                Treecell tcInfo = new Treecell();

                //System.out.print("name: " + joLayer.getString("displayname:") + " --- ");
                //System.out.println("type: " + joLayer.getString("type"));

                if (!joLayer.getString("type").equals("node")) {

                    // add the "add" button
                    //tcAdd = new Treecell();
                    tcAdd.setImage("/img/add.png");

                    // add the "info" button
                    //tcInfo = new Treecell();
                    tcInfo.setImage("/img/information.png");

                    // set the layer data for the row
                    tr.setAttribute("lyr", joLayer);
                } else {
                }

                if (joLayer.getString("type").equals("class")) {
                    tcAdd.setImage("/img/add.png");
                }

                String displayname = joLayer.getString("displayname");
                displayname = (displayname.contains(">"))? displayname.split(">")[1]:displayname;
                Treecell tcName = new Treecell(displayname);
                //Treecell  tcDesc = new Treecell(joLayer.getString("displayname"));


                // Attach onclick events:
                if (!joLayer.getString("type").equals("node")) {

                    tcAdd.addEventListener("onClick", new EventListener() {

                        @Override
                        public void onEvent(Event event) throws Exception {
                            Object o = event.getTarget().getId();
                            Treecell tc = (Treecell) event.getTarget();
                            JSONObject joLayer = JSONObject.fromObject(tc.getParent().getAttribute("lyr"));
                            if (!joLayer.getString("type").contentEquals("class")) {
                            System.out.println("Loading layer: " + joLayer.getString("displayname") + " from " + joLayer.getString("displaypath"));

//                            String metadata = joLayer.getString("metadatapath");
//                            if (metadata.equals("")) {
//                                metadata += "Name: " + joLayer.getString("displayname") + "\n";
//                                metadata += "Classification: " + joLayer.getString("classification1") + "\n";
//                                metadata += "Source: " + joLayer.getString("source") + "\n";
//                                metadata += "Sample: " + joLayer.getString("displaypath") + "\n";
//                            }

                            String metadata = satServer + "/alaspatial/layers/" + joLayer.getString("uid");

                            mc.addWMSLayer(joLayer.getString("displayname"),
                                    joLayer.getString("displaypath"),
                                    (float) 0.75, metadata);
                            }
                            else {
                                String classAttribute = joLayer.getString("classname");
                                String classValue = joLayer.getString("displayname");
                                String layer = joLayer.getString("layername");
                                String displaypath = joLayer.getString("displaypath") + "&cql_filter=(" + classAttribute + "='" + classValue + "');include";
                                //Filtered requests don't work on
                                displaypath = displaypath.replace("gwc/service/","");
                               // Messagebox.show(displaypath);
                                String metadata = satServer + "/alaspatial/layers/" + joLayer.getString("uid");

                                mc.addWMSLayer(layer + " - " + classValue,
                                    displaypath,
                                    (float) 0.75, metadata);
                            }
                        }
                    });


                    tcInfo.addEventListener("onClick", new EventListener() {

                        @Override
                        public void onEvent(Event event) throws Exception {

                            Object o = event.getTarget().getId();
                            Treecell tc = (Treecell) event.getTarget();
                            JSONObject joLayer = JSONObject.fromObject(tc.getParent().getAttribute("lyr"));
                            
                             String metadata = satServer + "/alaspatial/layers/" + joLayer.getString("uid");
//                                Clients.evalJavaScript("window.open('"
//                                        + metadata
//                                        + "', 'metadataWindow');");

                            mc.activateLink(metadata, "Metadata", false);
                          
                        }

                    });
                }

                //Attach treecells to treerow
                tcName.setParent(tr);
                tcAdd.setParent(tr);
                tcInfo.setParent(tr);
                item.setOpen(false);
            }
        });

    }
}
