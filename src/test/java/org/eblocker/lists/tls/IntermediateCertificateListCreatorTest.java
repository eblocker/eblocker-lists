/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.lists.tls;

import org.eblocker.crypto.pki.PKI;
import org.eblocker.lists.util.HttpClient;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.security.auth.x500.X500Principal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;

public class IntermediateCertificateListCreatorTest {

    private static final String INTERMEDIATE_CERTIFICATES_URL = "http://ccadb.com/intermediate.pem";

    private Path outputFile;
    private HttpClient httpClient;
    private IntermediateCertificateListCreator creator;

    @Before
    public void setUp() throws IOException {
        outputFile = Files.createTempFile("intermediate-certificates",  ".pem");
        Files.delete(outputFile);

        httpClient = Mockito.mock(HttpClient.class);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(outputFile);
    }

    @Test
    public void testDownload() throws Exception {
        Mockito.when(httpClient.download(INTERMEDIATE_CERTIFICATES_URL, null, null)).thenReturn(ClassLoader.getSystemResourceAsStream("intermediate-certificates.csv"));

        creator = new IntermediateCertificateListCreator(INTERMEDIATE_CERTIFICATES_URL, outputFile.toString(), 0, 100, httpClient);
        creator.run();

        Assert.assertTrue(Files.exists(outputFile));
        X509Certificate[] certificates;
        try(InputStream in = Files.newInputStream(outputFile)) {
            certificates = PKI.loadCertificates(in);
        }
        Assert.assertEquals(2, certificates.length);
        Assert.assertEquals(new X500Principal("CN=RACER, O=AC Camerfirma SA, SERIALNUMBER=A82743287, L=Madrid (see current address at www.camerfirma.com/address), EMAILADDRESS=caracer@camerfirma.com, C=ES"), certificates[0].getSubjectX500Principal());
        Assert.assertEquals(new X500Principal("CN=COMODO ECC Certification Authority, O=COMODO CA Limited, L=Salford, ST=Greater Manchester, C=GB"), certificates[1].getSubjectX500Principal());
    }

    @Test(expected = IntermediateCertificateListCreator.IntermediateCertificateException.class)
    public void testDownloadTooFewCertificates() throws Exception {
        Mockito.when(httpClient.download(INTERMEDIATE_CERTIFICATES_URL, null, null)).thenReturn(ClassLoader.getSystemResourceAsStream("intermediate-certificates.csv"));

        creator = new IntermediateCertificateListCreator(INTERMEDIATE_CERTIFICATES_URL, outputFile.toString(), 90, 100, httpClient);
        creator.run();
    }

    @Test(expected = IntermediateCertificateListCreator.IntermediateCertificateException.class)
    public void testDownloadTooManyCertificates() throws Exception {
        Mockito.when(httpClient.download(INTERMEDIATE_CERTIFICATES_URL, null, null)).thenReturn(ClassLoader.getSystemResourceAsStream("intermediate-certificates.csv"));

        creator = new IntermediateCertificateListCreator(INTERMEDIATE_CERTIFICATES_URL, outputFile.toString(), 0, 1, httpClient);
        creator.run();
    }

    @Test(expected = IntermediateCertificateListCreator.IntermediateCertificateException.class)
    public void testDownloadFailure() throws IOException, IntermediateCertificateListCreator.IntermediateCertificateException {
        Mockito.when(httpClient.download(INTERMEDIATE_CERTIFICATES_URL, null, null)).thenThrow(IOException.class);

        creator = new IntermediateCertificateListCreator(INTERMEDIATE_CERTIFICATES_URL, outputFile.toString(), 0, 100, httpClient);
        creator.run();
    }


}