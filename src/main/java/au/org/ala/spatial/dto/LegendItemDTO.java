package au.org.ala.spatial.dto;

import au.org.ala.spatial.StringConstants;

/**
 * A dto that represents a legend item
 *
 * @author Natasha Quimby (natasha.quimby@csiro.au)
 */
public class LegendItemDTO {

    private String name = StringConstants.UNKNOWN;
    private Integer count;
    private Integer red;
    private Integer green;
    private Integer blue;

    public LegendItemDTO() {

    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the count
     */
    public Integer getCount() {
        return count;
    }

    /**
     * @param count the count to set
     */
    public void setCount(Integer count) {
        this.count = count;
    }

    /**
     * @return the red
     */
    public Integer getRed() {
        return red;
    }

    /**
     * @param red the red to set
     */
    public void setRed(Integer red) {
        this.red = red;
    }

    /**
     * @return the green
     */
    public Integer getGreen() {
        return green;
    }

    /**
     * @param green the green to set
     */
    public void setGreen(Integer green) {
        this.green = green;
    }

    /**
     * @return the blue
     */
    public Integer getBlue() {
        return blue;
    }

    /**
     * @param blue the blue to set
     */
    public void setBlue(Integer blue) {
        this.blue = blue;
    }


}