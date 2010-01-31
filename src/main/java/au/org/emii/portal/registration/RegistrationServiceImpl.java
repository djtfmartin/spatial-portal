/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.emii.portal.registration;

import au.org.emii.portal.registration.RegistrationService;
import au.org.emii.portal.user.UserInfoService;
import au.org.emii.portal.webservice.UserManagementWebService;
import au.org.emii.portal.webservice.UserManagementWebServiceEndpointImpl;
import au.org.emii.portal.webservice.XmlWebService;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.log4j.Logger;

/**
 *
 * @author geoff
 */
public class RegistrationServiceImpl extends UserManagementWebServiceEndpointImpl implements RegistrationService {

    /**
     * Contents of the /response/result/text() element to indicate
     * a duplicate user has been added.  Normally
     * errorEmailAddressAlreadyRegistered
     */
    private String duplicateUserErrorMessage = null;
    /**
     * Profile to request - manditory parameter but should always be
     * RegisteredUser
     */
    private String profile = null;
    /**
     * URI to make requests to
     */
    private String serviceUri = null;
    /**
     * XML request processor - gets injected by spring
     */
    private XmlWebService xmlWebService = null;
    /**
     * Log4j
     */
    private Logger logger = Logger.getLogger(getClass());

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getServiceUri() {
        return serviceUri;
    }

    public void setServiceUri(String serviceUri) {
        this.serviceUri = serviceUri;
    }

    public XmlWebService getXmlWebService() {
        return xmlWebService;
    }

    public void setXmlWebService(XmlWebService xmlWebService) {
        this.xmlWebService = xmlWebService;
    }

    public String getDuplicateUserErrorMessage() {
        return duplicateUserErrorMessage;
    }

    public void setDuplicateUserErrorMessage(String duplicateUserErrorMessage) {
        this.duplicateUserErrorMessage = duplicateUserErrorMessage;
    }


    @Override
    public int register(String lastName, String firstName, String email, String address, String state, String zip, String country, String organisation, String affiliation) {
        return register(getDefaultUserManagementServiceId(), lastName, firstName, email, address, state, zip, country, organisation, affiliation);
    }

    @Override
    public int register(String service, String lastName, String firstName, String email, String address, String state, String zip, String country, String organisation, String affiliation) {

        serviceUri = getUserManagementWebServiceById(service).selfRegistrationServiceUri();
        int status = REGISTRATION_UNKNOWN_ERROR;

        // setup web service parameters
        MultivaluedMap queryParams = new MultivaluedMapImpl();

        // mandatory parameters
        queryParams.add("surname", lastName);
        queryParams.add("name", firstName);
        queryParams.add("email", email);

        // optional parameters
        queryParams.add("address", address);
        queryParams.add("state", state);
        queryParams.add("zip", zip);
        queryParams.add("country", country);
        queryParams.add("organisation", organisation);
        queryParams.add("kind", affiliation);

        // profile parameter
        queryParams.add("profile", profile);

        xmlWebService.makeRequest(serviceUri, queryParams);

        if (xmlWebService.isResponseXml()) {
            String errorMessage = xmlWebService.parseString("//response/result/text()");
            if (errorMessage != null && errorMessage.equals(duplicateUserErrorMessage)) {
                status = REGISTRATION_FAIL_DUPLICATE;
            } else {
                String replySurname = xmlWebService.parseString("response/@surname");
                String replyName = xmlWebService.parseString("response/@name");
                String replyEmail = xmlWebService.parseString("response/@email");
                String replyUsername = xmlWebService.parseString("response/@username");

                // check the response matches what we sent
                if (replySurname != null && replySurname.equals(lastName)
                        && replyName != null && replyName.equals(firstName)
                        && replyEmail != null && replyEmail.equals(email)
                        && replyUsername != null && replyUsername.equals(email)) {

                    // all good
                    status = REGISTRATION_OK;
                } else {
                    // hmm registration looks like it succeeded but doesn't match the criteria
                    // for success - unknown error (default status) and log the result
                    logger.error(
                            "Unknown response from registration system at '" + serviceUri + "': '"
                            + xmlWebService.getRawResponse() + "'");
                }
            }
        } else {
            // non-xml error - normally indicates missing data
            status = REGISTRATION_FAIL_INCOMPLETE;
        }
        return status;
    }
}
