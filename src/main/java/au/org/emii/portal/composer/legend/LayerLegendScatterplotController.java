/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.emii.portal.composer.legend;

import au.org.ala.legend.Facet;
import au.org.ala.spatial.StringConstants;
import au.org.ala.spatial.dto.ScatterplotDataDTO;
import au.org.ala.spatial.util.CommonData;
import au.org.ala.spatial.util.Query;
import au.org.ala.spatial.util.Util;
import au.org.emii.portal.composer.MapComposer;
import au.org.emii.portal.composer.UtilityComposer;
import au.org.emii.portal.menu.HasMapLayer;
import au.org.emii.portal.menu.MapLayer;
import au.org.emii.portal.menu.SelectedArea;
import au.org.emii.portal.util.LayerUtilitiesImpl;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONArray;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.*;

import java.io.InputStream;
import java.net.URL;

/**
 * @author Adam
 */
public class LayerLegendScatterplotController extends UtilityComposer implements HasMapLayer {

    private static final Logger LOGGER = Logger.getLogger(LayerLegendScatterplotController.class);

    private Textbox tbxChartSelection;
    private Label tbxSelectionCount;
    private Label tbxRange;
    private Label tbxDomain;
    private Label tbxMissingCount;
    private ScatterplotDataDTO data;
    private Div scatterplotButtons;
    private Div scatterplotDownloads;
    private MapLayer mapLayer = null;
    private Checkbox chkSelectMissingRecords;

    private Button addNewLayers;
    private Combobox cbHighlightArea;
    private ScatterplotLayerLegendComposer layerWindow = null;

    @Override
    public void afterCompose() {
        super.afterCompose();

        this.addEventListener("onSize", new EventListener() {

            @Override
            public void onEvent(Event event) throws Exception {
                redraw();
            }
        });
    }

    @Override
    public void doEmbedded() {
        super.doEmbedded();
        redraw();
    }

    @Override
    public void doOverlapped() {
        super.doOverlapped();
        redraw();
    }

    public ScatterplotDataDTO getScatterplotData() {
        if (data == null) {
            if (mapLayer == null) {
                data = new ScatterplotDataDTO();
            } else {
                data = mapLayer.getScatterplotDataDTO();
            }
        }
        return data;
    }

    public void onChange$tbxChartSelection(Event event) {
        try {
            //input order is [x, y, width, height]
            // + optional bounding box coordinates [x1,y1,x2,y2]
            LOGGER.debug(event.getData());
            String[] coordsStr = ((String) event.getData()).replace("px", "").split(",");
            double[] coordsDbl = new double[coordsStr.length];
            for (int i = 0; i < coordsStr.length; i++) {
                coordsDbl[i] = Double.parseDouble(coordsStr[i]);
            }

            String params = "?minx=" + coordsDbl[0] + "&miny=" + coordsDbl[1] + "&maxx=" + coordsDbl[2] + "&maxy=" + coordsDbl[3];

            data.setImagePath(CommonData.getSatServer() + "/ws/scatterplot/" + data.getId() + params);

            ObjectMapper om = new ObjectMapper();
            JSONArray ja = om.readValue(new URL(data.getImagePath()), JSONArray.class);

            data.setPrevSelection(new double[4]);
            data.getPrevSelection()[0] = Double.parseDouble(ja.get(0).toString());
            data.getPrevSelection()[1] = Double.parseDouble(ja.get(1).toString());
            data.getPrevSelection()[2] = Double.parseDouble(ja.get(2).toString());
            data.getPrevSelection()[3] = Double.parseDouble(ja.get(3).toString());

            Facet f = getFacetIn();
            if (f != null) {
                mapLayer.setHighlight(f.toString());
            } else {
                mapLayer.setHighlight(null);
            }

            getMapComposer().applyChange(mapLayer);

            tbxChartSelection.setText("");
            tbxDomain.setValue(String.format("%s: %g - %g", data.getLayer1Name(), data.getPrevSelection()[1], data.getPrevSelection()[3]));
            tbxRange.setValue(String.format("%s: %g - %g", data.getLayer2Name(), data.getPrevSelection()[0], data.getPrevSelection()[2]));

            data.setImagePath(CommonData.getSatServer() + "/ws/scatterplot/" + data.getId() + ".png" + params);

            registerScatterPlotSelection();

            redraw();
        } catch (Exception e) {
            LOGGER.error("failed to build scatterplot legend", e);
            clearSelection();
            getMapComposer().applyChange(mapLayer);
        }
    }

    void redraw() {
        getScatterplotData();
        updateCbHighlightArea();

        int width = Integer.parseInt(this.getWidth().replace("px", "")) - 20;
        int height = Integer.parseInt(this.getHeight().replace("px", "")) - Integer.parseInt(tbxChartSelection.getHeight().replace("px", ""));
        if (height > width) {
            height = width;
        } else {
            width = height;
        }

        if (data.getImagePath() == null || !data.getImagePath().contains(".png")) {
            data.setImagePath(CommonData.getSatServer() + "/ws/scatterplot/" + data.getId() + ".png?" + System.currentTimeMillis());
        }
        String script = "if (typeof updateScatterplot !== 'undefined') updateScatterplot(" + width + "," + height + ",'url(" + data.getImagePath() + ")')";
        Clients.evalJavaScript(script);

        scatterplotDownloads.setVisible(true);

        updateCount(String.valueOf(data.getSelectionCount()));
        if (data.getMissingDataChecked() != chkSelectMissingRecords.isChecked()) {
            chkSelectMissingRecords.setChecked(data.getMissingDataChecked());
        }

        if (data.getMissingCount() > 0) {
            tbxMissingCount.setValue("(" + data.getMissingCount() + ")");
            chkSelectMissingRecords.setVisible(true);
        } else {
            tbxMissingCount.setValue("");
            chkSelectMissingRecords.setVisible(false);
        }
    }

    private void registerScatterPlotSelection() {
        try {
            if (data.getLayer1() != null && data.getLayer1().length() > 0
                    && data.getLayer2() != null && data.getLayer2().length() > 0) {
                Facet f = getFacetIn();

                int count = 0;
                if (f != null) {
                    Query q = data.getQuery().newFacet(f, false);
                    count = q.getOccurrenceCount();
                }
                updateCount(String.valueOf(count));
            }
        } catch (Exception e) {
            LOGGER.error("error updating scatterplot selection", e);
            clearSelection();
            getMapComposer().applyChange(mapLayer);
        }
    }

    void updateCount(String txt) {
        if (txt != null && !txt.isEmpty()) {
            data.setSelectionCount(Integer.parseInt(txt));

            //cannot facet when layers are not indexed
            String e1 = CommonData.getLayerFacetName(data.getLayer1());
            String e2 = CommonData.getLayerFacetName(data.getLayer2());
            if (!CommonData.getBiocacheLayerList().contains(e1)) {
                tbxSelectionCount.setValue("Cannot select records. " + data.getLayer1Name() + " is not indexed");
                return;
            }
            if (!CommonData.getBiocacheLayerList().contains(e2)) {
                tbxSelectionCount.setValue("Cannot select records. " + data.getLayer2Name() + " is not indexed");
                return;
            }

            tbxSelectionCount.setValue("Records selected: " + txt);
            if (data.getSelectionCount() > 0) {
                addNewLayers.setVisible(true);
            } else {
                addNewLayers.setVisible(false);
            }
            scatterplotButtons.setVisible(true);
        }
    }

    void clearSelection() {
        tbxSelectionCount.setValue("");
        addNewLayers.setVisible(false);
        tbxRange.setValue("");
        tbxDomain.setValue("");

        data.setPrevSelection(null);

        chkSelectMissingRecords.setChecked(false);

        getScatterplotData().setEnabled(false);

        scatterplotDownloads.setVisible(false);

        data.setPrevSelection(null);

        scatterplotButtons.setVisible(false);
    }

    public void onClick$addSelectedRecords(Event event) {
        Facet f = getFacetIn();
        if (f != null) {
            addUserLayer(data.getQuery().newFacet(getFacetIn(), true), "IN " + data.getSpeciesName(), null, 0);
        }
    }

    public void onClick$addUnSelectedRecords(Event event) {
        addUserLayer(data.getQuery().newFacet(getFacetOut(), true), "OUT " + data.getSpeciesName(), null, 0);
    }

    void addUserLayer(Query query, String layername, String description, int numRecords) {
        String cLayername = StringUtils.capitalize(layername);

        getMapComposer().mapSpecies(query, cLayername, StringConstants.SPECIES, -1, LayerUtilitiesImpl.SPECIES, null, -1, MapComposer.DEFAULT_POINT_SIZE,
                MapComposer.DEFAULT_POINT_OPACITY, Util.nextColour(), false);
    }

    public void onClick$addNewLayers(Event event) {
        onClick$addUnSelectedRecords(null);
        onClick$addSelectedRecords(null);
    }

    public void onClick$scatterplotImageDownload(Event event) {
        getScatterplotData();

        try {
            byte[] b = null;
            InputStream in = new URL(data.getImagePath()).openStream();
            try {
                b = IOUtils.toByteArray(in);
            } finally {
                IOUtils.closeQuietly(in);
            }

            Filedownload.save(b, StringConstants.IMAGE_PNG, "scatterplot.png");
        } catch (Exception e) {
            LOGGER.error("error saving scatterplot image: " + data.getImagePath(), e);
        }
    }

    public void onClick$scatterplotDataDownload(Event event) {
        getScatterplotData();

        try {
            String csv = null;
            InputStream in = new URL(CommonData.getSatServer() + "/ws/scatterplot/csv/" + data.getId()).openStream();
            try {
                csv = IOUtils.toString(in);
            } finally {
                IOUtils.closeQuietly(in);
            }

            Filedownload.save(csv, StringConstants.TEXT_PLAIN, "scatterplot.csv");
        } catch (Exception e) {
            LOGGER.error("error downloading scatterplot csv for id: " + data.getId(), e);
        }
    }

    public void onCheck$chkSelectMissingRecords(Event event) {
        try {
            registerScatterPlotSelection();

            ScatterplotDataDTO d = getScatterplotData();
            d.setEnabled(true);

            Facet f = getFacetIn();
            if (f == null) {
                mapLayer.setHighlight(null);
            } else {
                mapLayer.setHighlight(f.toString());
            }

            getMapComposer().applyChange(mapLayer);

            tbxChartSelection.setText("");

            data.setImagePath(null);
            data.setMissingDataChecked(chkSelectMissingRecords.isChecked());
            redraw();
        } catch (Exception e) {
            LOGGER.error("error toggling missing records checkbox", e);
            clearSelection();
            getMapComposer().applyChange(mapLayer);
        }
    }

    public void updateFromLegend() {
        updateFromLegend(
                layerWindow.getRed(),
                layerWindow.getGreen(),
                layerWindow.getBlue(),
                layerWindow.getOpacity(),
                layerWindow.getPlotSize(),
                layerWindow.getColourMode());

        if (mapLayer != null) {
            mapLayer.setColourMode(layerWindow.getColourMode());
            mapLayer.setRedVal(layerWindow.getRed());
            mapLayer.setGreenVal(layerWindow.getGreen());
            mapLayer.setBlueVal(layerWindow.getBlue());
            mapLayer.setOpacity(layerWindow.getOpacity() / 100.0f);
            mapLayer.setSizeVal(layerWindow.getSize());

            getMapComposer().applyChange(mapLayer);
        }
    }

    public void updateFromLegend(int red, int green, int blue, int opacity, int size, String colourMode) {
        data.setRed(red);
        data.setGreen(green);
        data.setBlue(blue);
        data.setOpacity(opacity);
        data.setSize(size);
        data.setColourMode(colourMode);

        try {
            HttpClient client = new HttpClient();
            PostMethod post = new PostMethod(CommonData.getSatServer() + "/ws/scatterplot/style/" + data.getId());

            //set style parameters that can change here
            post.addParameter("colourMode", String.valueOf(data.getColourMode()));
            post.addParameter(StringConstants.RED, String.valueOf(data.getRed()));
            post.addParameter(StringConstants.GREEN, String.valueOf(data.getGreen()));
            post.addParameter(StringConstants.BLUE, String.valueOf(data.getBlue()));
            post.addParameter(StringConstants.OPACITY, String.valueOf(data.getOpacity()));
            post.addParameter(StringConstants.SIZE, String.valueOf(data.getSize()));

            post.addRequestHeader(StringConstants.ACCEPT, StringConstants.APPLICATION_JSON);

            client.executeMethod(post);

        } catch (Exception e) {
            LOGGER.error("error getting a new scatterplot id", e);
        }

        data.setImagePath(null);
        redraw();
    }

    private Facet getFacetIn() {
        String fq = null;
        String e1 = CommonData.getLayerFacetName(data.getLayer1());
        String e2 = CommonData.getLayerFacetName(data.getLayer2());

        //cannot facet when layers are not indexed
        if (!CommonData.getBiocacheLayerList().contains(e1) || !CommonData.getBiocacheLayerList().contains(e2)) {
            return null;
        }

        if (chkSelectMissingRecords.isChecked() && data.getPrevSelection() == null) {
            fq = "-(" + e1 + ":[* TO *] AND " + e2 + ":[* TO *])";
        } else if (data.getPrevSelection() != null) {
            double x1 = data.getPrevSelection()[0];
            double x2 = data.getPrevSelection()[2];
            double y1 = data.getPrevSelection()[1];
            double y2 = data.getPrevSelection()[3];

            Facet f1 = new Facet(e1, y1, y2, true);
            Facet f2 = new Facet(e2, x1, x2, true);

            if (chkSelectMissingRecords.isChecked()) {
                fq = "-(-(" + f1.toString() + " AND " + f2.toString() + ") AND " + e1 + ":[* TO *] AND " + e2 + ":[* TO *])";
            } else {
                fq = f1.toString() + " AND " + f2.toString();
            }
        }

        return Facet.parseFacet(fq);
    }

    private Facet getFacetOut() {
        String fq = "*:*";
        String e1 = CommonData.getLayerFacetName(data.getLayer1());
        String e2 = CommonData.getLayerFacetName(data.getLayer2());

        //cannot facet when layers are not indexed
        if (!CommonData.getBiocacheLayerList().contains(e1) || !CommonData.getBiocacheLayerList().contains(e2)) {
            return null;
        }

        if (chkSelectMissingRecords.isChecked() && data.getPrevSelection() == null) {
            fq = e1 + ":[* TO *] AND " + e2 + ":[* TO *]";
        } else if (data.getPrevSelection() != null) {
            double x1 = data.getPrevSelection()[0];
            double x2 = data.getPrevSelection()[1];
            double y1 = data.getPrevSelection()[2];
            double y2 = data.getPrevSelection()[3];

            Facet f1 = new Facet(e1, y1, y2, true);
            Facet f2 = new Facet(e2, x1, x2, true);
            if (chkSelectMissingRecords.isChecked()) {
                fq = "-(" + f1.toString() + " AND " + f2.toString() + ") AND " + e1 + ":[* TO *] AND " + e2 + ":[* TO *]";
            } else {
                fq = "-(" + f1.toString() + " AND " + f2.toString() + ")";
            }
        }

        return Facet.parseFacet(fq);
    }

    private void updateCbHighlightArea() {
        for (int i = cbHighlightArea.getItemCount() - 1; i >= 0; i--) {
            cbHighlightArea.removeItemAt(i);
        }

        boolean selectionSuccessful = false;
        for (MapLayer ml : getMapComposer().getPolygonLayers()) {
            Comboitem ci = new Comboitem(ml.getDisplayName());
            ci.setValue(ml);
            ci.setParent(cbHighlightArea);
            if (data != null && data.getHighlightSa() != null
                    && data.getHighlightSa().getMapLayer().getName().equals(ml.getName())) {
                cbHighlightArea.setSelectedItem(ci);
                selectionSuccessful = true;
            }
        }

        //this may be a deleted layer or current view or au or world
        if (!selectionSuccessful && data != null
                && data.getHighlightSa() != null) {
            MapLayer ml = data.getHighlightSa().getMapLayer();
            if (ml != null) {
                Comboitem ci = new Comboitem(ml.getDisplayName() + " (DELETED LAYER)");
                ci.setValue(ml);
                ci.setParent(cbHighlightArea);
                cbHighlightArea.setSelectedItem(ci);
            } else {
                String name = "Previous area";
                if (data.getHighlightSa().getWkt() != null) {
                    if (data.getHighlightSa().getWkt().equals(CommonData.getSettings().getProperty(CommonData.AUSTRALIA_WKT))) {
                        name = CommonData.getSettings().getProperty(CommonData.AUSTRALIA_NAME);
                    } else if (data.getHighlightSa().getWkt().equals(CommonData.WORLD_WKT)) {
                        name = "World";
                    }
                }
                Comboitem ci = new Comboitem(name);
                ci.setValue(data.getHighlightSa().getWkt());
                ci.setParent(cbHighlightArea);
                cbHighlightArea.setSelectedItem(ci);
            }
        }
    }

    public void onSelect$cbHighlightArea(Event event) {
        if (cbHighlightArea.getSelectedItem() != null) {
            if (cbHighlightArea.getSelectedItem().getValue() instanceof MapLayer) {
                MapLayer ml = cbHighlightArea.getSelectedItem().getValue();
                SelectedArea sa = new SelectedArea(ml, ml.getFacets() == null ? ml.getWKT() : null);
                data.setHighlightSa(sa);
            } else {
                String wkt = cbHighlightArea.getSelectedItem().getValue();
                SelectedArea sa = new SelectedArea(null, wkt);
                data.setHighlightSa(sa);
            }
        } else {
            data.setHighlightSa(null);
        }

        try {
            HttpClient client = new HttpClient();
            PostMethod post = new PostMethod(CommonData.getSatServer() + "/ws/scatterplot/style/" + data.getId());

            //add style parameters (highlight area)
            if (data.getHighlightSa() != null) {
                post.addParameter(StringConstants.HIGHLIGHT_WKT, data.getHighlightSa().getWkt());
            } else {
                post.addParameter(StringConstants.HIGHLIGHT_WKT, "");
            }

            post.addRequestHeader(StringConstants.ACCEPT, StringConstants.APPLICATION_JSON);

            client.executeMethod(post);

        } catch (Exception e) {
            LOGGER.error("error getting a new scatterplot id", e);
        }

        data.setImagePath(null);
        redraw();
    }

    public void onClick$bClearHighlightArea(Event event) {
        cbHighlightArea.setSelectedIndex(-1);
        onSelect$cbHighlightArea(null);
    }

    @Override
    public void setMapLayer(MapLayer mapLayer) {
        this.mapLayer = mapLayer;
    }

    public void onClick$btnEditAppearance1(Event event) {
        if (layerWindow != null) {
            boolean closing = layerWindow.getParent() != null;
            layerWindow.detach();
            layerWindow = null;

            if (closing) {
                return;
            }
        }

        getScatterplotData();

        if (data.getQuery() != null) {
            //preview species list
            layerWindow = (ScatterplotLayerLegendComposer) Executions.createComponents("WEB-INF/zul/legend/ScatterplotLayerLegend.zul", getRoot(), null);
            EventListener el = new EventListener() {

                @Override
                public void onEvent(Event event) throws Exception {
                    updateFromLegend();
                }
            };
            layerWindow.init(data.getQuery(), mapLayer, data.getRed(), data.getGreen(), data.getBlue(), data.getSize(), data.getOpacity(), data.getColourMode(), el);

            try {
                layerWindow.doOverlapped();
                layerWindow.setPosition("right");
            } catch (Exception e) {
                LOGGER.error("failed ot open scatteplot layer legend", e);
            }
        }
    }

}
