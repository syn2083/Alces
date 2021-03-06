/*
 * Created on Apr 22, 2005
 */
package org.cip4.tools.alces.preprocessor.jmf;

import org.apache.log4j.Logger;
import org.cip4.jdflib.jmf.JDFJMF;
import org.cip4.tools.alces.message.Message;
import org.cip4.tools.alces.preprocessor.PreprocessorContext;
import org.cip4.tools.alces.util.JDFConstants;

/**
 * Preprocesses a JMF message by replacing JMF/@SenderID.
 * 
 * @author Claes Buckwalter (clabu@itn.liu.se)
 */
public class SenderIDPreprocessor implements Preprocessor {

	public static final String SENDERID_ATTR = "org.cip4.tools.alces.SenderIDPreprocessor.SenderID";

	private String senderIDValue;

	private static Logger LOGGER = Logger.getLogger(SenderIDPreprocessor.class);

	public SenderIDPreprocessor() {
		this(null);
	}

	public SenderIDPreprocessor(String senderId) {
		setSenderID(senderId);
	}

	public void setSenderID(String senderId) {
		senderIDValue = senderId;
	}

	public String getSenderID() {
		return senderIDValue;
	}

	/**
	 * Preprocesses a JMF message by replacing SenderID.
	 * 
	 * @see org.mule.transformers.AbstractTransformer#doTransform(java.lang.Object)
	 */
	public Message preprocess(final Message message) {
		return preprocess(message, null);
	}

	public Message preprocess(Message message, PreprocessorContext context) {
		if (!message.getContentType().startsWith(JDFConstants.JMF_CONTENT_TYPE)) {
			LOGGER.debug("Message not preprocessed because it did not contain JMF. Content-type was: " + message.getContentType());
			return message;
		}
		// Get SenderID
		final String senderId;
		if (context != null && context.getAttribute(SENDERID_ATTR) != null) {
			senderId = (String) context.getAttribute(SENDERID_ATTR);
		} else if (getSenderID() != null) {
			senderId = getSenderID();
		} else {
			senderId = "Alces SenderIDPreprocessor is not configured with a SenderID";
		}

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Preprocessor input: " + message.toString());
		}

		JDFJMF jmf = message.getBodyAsJMF();
		jmf.setSenderID(senderId);
		message.setBody(jmf.toXML());

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Preprocessor output: " + message);
		}
		return message;
	}
}