package org.cip4.tools.alces.test.tests;

import java.util.List;

import org.cip4.jdflib.jmf.JDFJMF;
import org.cip4.jdflib.jmf.JDFMessage;
import org.cip4.tools.alces.message.Message;
import org.cip4.tools.alces.test.TestResult;
import org.cip4.tools.alces.test.TestResult.Result;
import org.cip4.tools.alces.test.TestResultImpl;
import org.cip4.tools.alces.util.JDFConstants;

/**
 * Checks whether the ReturnCode of JDFResponses and JDFAcknowledges equals 0
 * ("the message has been processed successfully")
 * 
 * @author Niels Boeger
 */
public class ReturnCodeTest extends Test {

    public ReturnCodeTest() {
        super(
                "ReturnCodeTest - check whether the ReturnCode of all "
                        + "JDFResponses and JDFAcknowledges equals 0 (the message has been processed successfully)");
    }

    @Override
	public TestResult runTest(Message message) {
        final TestResult result;    
        if (!message.getContentType().startsWith(JDFConstants.JMF_CONTENT_TYPE)) {
        	result = new TestResultImpl(this, message, Result.IGNORED, 
        			"Test ignored because message did not contain JMF. Message content-type was: " 
        			+ message.getContentType());
        } else {
        	boolean passedTest = true;	        
        	JDFJMF jmf = message.getBodyAsJMF();
	        List responses = jmf.getMessageVector(JDFMessage.EnumFamily.Response, null);
	        List acknowledges = jmf.getMessageVector(JDFMessage.EnumFamily.Acknowledge, null);
	        int numResponses = -1;
	        if (responses != null) {
	        	numResponses = responses.size();
	        }
	        int numAcks = -1;
	        if (acknowledges != null) {
	        	numAcks = acknowledges.size();
	        }
	        
	        int numRespFailures = 0;
	        int numAckFailures = 0;
	        StringBuffer failedResponses = new StringBuffer();	
	        for (int i = 0; i < numResponses; i++) {
	            boolean isValid = (jmf.getResponse(i).getReturnCode() == 0);
	            if (!isValid) {
	                failedResponses.append(jmf.getResponse(i).getJMFRoot().toXML()).append("\n-----------------------------\n");
	                numRespFailures++;
	                passedTest = false;
	            }
	        }
	        for (int i = 0; i < numAcks; i++) {
	            boolean isValid = (jmf.getAcknowledge(i).getReturnCode() == 0);
	            if (!isValid) {
	                failedResponses.append(jmf.getAcknowledge(i).getJMFRoot().toXML()).append("\n-----------------------------\n");
	                numAckFailures++;
	                passedTest = false;
	            }
	        }
	
	        String logMsg = "";
	        if (!(numResponses > 0 || numAcks > 0)) {
	            logMsg += "No Responses or Acknowledges were to be tested.\n";
	        } else if (passedTest)
	            logMsg += "Test successful.\n" + "Processed " + numResponses
	                    + " Response message(s) and " + numAcks
	                    + " Acknowledge message(s).";
	        else {
	            logMsg += "Test failed.\n" + numRespFailures + " out of "
	                    + numResponses + " Response message(s) and "
	                    + numAckFailures + " out of " + numAcks
	                    + " Acknowledge message(s) failed: \n" + failedResponses;
	        }
	
	        result = new TestResultImpl(this, message, Result.getPassed(passedTest), logMsg);
        }
        return result;
    }
}
