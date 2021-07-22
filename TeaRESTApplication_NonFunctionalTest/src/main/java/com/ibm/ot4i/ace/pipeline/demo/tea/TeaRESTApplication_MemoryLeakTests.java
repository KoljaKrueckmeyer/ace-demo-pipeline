package com.ibm.ot4i.ace.pipeline.demo.tea;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ibm.integration.test.v1.NodeSpy;
import com.ibm.integration.test.v1.NodeStub;
import com.ibm.integration.test.v1.SpyObjectReference;
import com.ibm.integration.test.v1.TestMessageAssembly;
import com.ibm.integration.test.v1.TestSetup;
import com.ibm.integration.test.v1.exception.TestException;

import static com.ibm.integration.test.v1.Matchers.*;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TeaRESTApplication_MemoryLeakTests {

        /*
         * TeaRESTApplication_getIndex_subflow_0001_Test
         * Test generated by IBM App Connect Enterprise Toolkit 12.0.1.0 on 10-Jun-2021 12:48:56
         */

        @AfterEach
        public void cleanupTest() throws TestException {
                // Ensure any mocks created by a test are cleared after the test runs 
                TestSetup.restoreAllMocks();
        }
        
        // Drive the flow externally
        public void callFlow(String path) throws IOException {
            URL obj = new URL("http://localhost:7800" + path);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Content-Type", "application/json; utf-8");
            int responseCode = con.getResponseCode();
            assertThat(responseCode,
                    Matchers.anyOf(Matchers.is(HttpURLConnection.HTTP_ACCEPTED), Matchers.is(HttpURLConnection.HTTP_OK)));
            con.disconnect();
        }
        /*
         * Return RSS value for the current process.
         */
        public int getMemoryUsage() throws IOException
        {
        	int retval = 0;
        	try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/status"))) {
				for(String line; (line = br.readLine()) != null; )
				{
					if ( line.startsWith("VmRSS:"))
					{
						String[] components = line.split("\\s+");
						// Now have VmRSS:, 123456, kB
						retval = Integer.parseInt(components[1]);
					}
				}
				br.close();
			}
        	return retval;
        }
        
        @Test
        public void TeaRESTApplication_CheckGETOperation_Test() throws Exception {
        
        	// Stub the nodes that would require an external DB (Get DB record) or spam
        	// the console (LogXMLData)
            SpyObjectReference dbNodeReference = new SpyObjectReference().application("TeaRESTApplication")
                    .messageFlow("gen.TeaRESTApplication").subflowNode("getIndex (Implementation)").subflowNode("GetFromDB").node("Get DB record");
            SpyObjectReference logNodeReference = new SpyObjectReference().application("TeaRESTApplication")
                    .messageFlow("gen.TeaRESTApplication").subflowNode("getIndex (Implementation)").subflowNode("LogAuditData").node("LogXMLData");

            // Find the message to use in place of the DB lookup
            String inputResourcePath = "/GetIndex_OutputMessage.mxml";
            InputStream resourceStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(inputResourcePath);
            if (resourceStream == null)
            {
                throw new TestException("Unable to locate resource: " + inputResourcePath);
            }
            TestMessageAssembly fixedOutputMessageAssembly = new TestMessageAssembly();
            fixedOutputMessageAssembly.buildFromRecordedMessageAssembly(resourceStream);

            // Run tests and check memory usage
            int [] memoryUsageSnapshot = new int[40];
            for ( int j=0 ; j < 40 ; j++ )
            {
            	for ( int i=0 ; i<20 ; i++ )
            	{
            		NodeStub dbNodeStub = new NodeStub(dbNodeReference);
            		dbNodeStub.onCall().propagatesMessage("in", "out", fixedOutputMessageAssembly);
            		NodeStub logNodeStub = new NodeStub(logNodeReference);
            		logNodeStub.onCall().propagatesInputMessage("in", "out");
            		callFlow("/tea/index/0");
            		dbNodeStub.restore();
            		logNodeStub.restore();
            	}       
            	// Check memory usage
            	memoryUsageSnapshot[j] = getMemoryUsage();
            }
            // Initial test of algorithm - seems to work reasonably well.
            int earlyAverage = ( memoryUsageSnapshot[5] + memoryUsageSnapshot[6] + memoryUsageSnapshot[7] + memoryUsageSnapshot[8] + memoryUsageSnapshot[9] ) / 5;
            int midAverage = ( memoryUsageSnapshot[20] + memoryUsageSnapshot[21] + memoryUsageSnapshot[22] + memoryUsageSnapshot[23] + memoryUsageSnapshot[24] ) / 5;
            int lateAverage = ( memoryUsageSnapshot[35] + memoryUsageSnapshot[36] + memoryUsageSnapshot[37] + memoryUsageSnapshot[38] + memoryUsageSnapshot[39] ) / 5;
            System.out.println("earlyAverage "+earlyAverage+" midAverage "+midAverage+" lateAverage "+lateAverage);
            
            // Java GC gets in our way slightly here - the memory numbers tend to bounce around quite a bit.
            int firstAverage = earlyAverage;
            if ( firstAverage < midAverage ) firstAverage = midAverage;
            
            assertThat("More than 1% growth in memory: "+firstAverage+" "+lateAverage, ( (lateAverage - firstAverage) < ( ( lateAverage/100 ) * 1 ) ) );
      }
}