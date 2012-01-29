package org.geotools.data.wfs.internal;

import static org.geotools.data.wfs.internal.AbstractWFSStrategy.WFS_1_0_0_CONFIGURATION;
import static org.geotools.data.wfs.internal.AbstractWFSStrategy.WFS_1_1_0_CONFIGURATION;
import static org.geotools.data.wfs.internal.AbstractWFSStrategy.WFS_2_0_0_CONFIGURATION;
import static org.geotools.data.wfs.internal.Loggers.MODULE;
import static org.geotools.data.wfs.internal.Loggers.RESPONSES;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.opengis.wfs.WFSCapabilitiesType;

import org.apache.commons.io.IOUtils;
import org.eclipse.emf.ecore.EObject;
import org.geotools.data.DataSourceException;
import org.geotools.data.ows.HTTPResponse;
import org.geotools.ows.ServiceException;
import org.geotools.util.Version;
import org.geotools.xml.Configuration;
import org.geotools.xml.Parser;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class GetCapabilitiesResponse extends org.geotools.data.ows.GetCapabilitiesResponse {

    private WFSGetCapabilities capabilities;

    public GetCapabilitiesResponse(HTTPResponse response) throws ServiceException, IOException {
        super(response);
        MODULE.finer("Parsing GetCapabilities response");
        try {
            final Document rawDocument;
            final byte[] rawResponse;
            {
                ByteArrayOutputStream buff = new ByteArrayOutputStream();
                InputStream inputStream = response.getResponseStream();
                try {
                    IOUtils.copy(inputStream, buff);
                } finally {
                    inputStream.close();
                }
                rawResponse = buff.toByteArray();
            }
            if (RESPONSES.isLoggable(Level.FINER)) {
                RESPONSES.finer("Full GetCapabilities response: " + new String(rawResponse));
            }
            try {
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                builderFactory.setNamespaceAware(true);
                builderFactory.setValidating(false);
                DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
                rawDocument = documentBuilder.parse(new ByteArrayInputStream(rawResponse));
            } catch (Exception e) {
                throw new IOException("Error parsing capabilities document: " + e.getMessage(), e);
            }

            List<Configuration> tryConfigs = Arrays.asList(WFS_2_0_0_CONFIGURATION,
                    WFS_1_1_0_CONFIGURATION, WFS_1_0_0_CONFIGURATION);

            final String versionAtt = rawDocument.getDocumentElement().getAttribute("version");
            Version version = null;
            if (null != versionAtt) {
                version = new Version(versionAtt);
                if (Versions.v1_0_0.equals(version)) {
                    tryConfigs = Collections.singletonList(WFS_1_0_0_CONFIGURATION);
                } else if (Versions.v1_1_0.equals(version)) {
                    tryConfigs = Collections.singletonList(WFS_1_1_0_CONFIGURATION);
                } else if (Versions.v2_0_0.equals(version)) {
                    tryConfigs = Collections.singletonList(WFS_2_0_0_CONFIGURATION);
                }
            }
            EObject parsedCapabilities = null;

            for (Configuration wfsConfig : tryConfigs) {
                try {
                    ByteArrayInputStream capabilitiesReader = new ByteArrayInputStream(rawResponse);
                    parsedCapabilities = parseCapabilities(capabilitiesReader, wfsConfig);
                    if (parsedCapabilities != null) {
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // if (object instanceof ServiceException) {
            // throw (ServiceException) object;
            // }

            this.capabilities = WFSGetCapabilities.create(parsedCapabilities, rawDocument);
        } finally {
            response.dispose();
        }
    }

    private EObject parseCapabilities(InputStream capabilitiesReader, final Configuration wfsConfig)
            throws IOException {

        final Parser parser = new Parser(wfsConfig);
        final Object parsed;
        try {
            parsed = parser.parse(capabilitiesReader);
        } catch (SAXException e) {
            throw new DataSourceException("Exception parsing WFS capabilities", e);
        } catch (ParserConfigurationException e) {
            throw new DataSourceException("WFS parsing configuration error", e);
        }
        if (parsed == null) {
            throw new DataSourceException("WFS capabilities was not parsed");
        }
        if (!(parsed instanceof WFSCapabilitiesType)) {
            throw new DataSourceException("Expected WFS Capabilities, got " + parsed);
        }
        EObject object = (EObject) parsed;
        return object;
    }

    @Override
    public WFSGetCapabilities getCapabilities() {
        return capabilities;
    }
}