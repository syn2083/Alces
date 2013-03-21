/*
 * Created on Apr 22, 2005
 */
package org.cip4.elk.alces.preprocessor.jmf;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cip4.elk.alces.message.Message;
import org.cip4.elk.alces.preprocessor.PreprocessorContext;
import org.cip4.elk.alces.preprocessor.PreprocessorException;
import org.cip4.elk.alces.util.JDFConstants;
import org.cip4.jdflib.jmf.JDFJMF;
import org.cip4.jdflib.util.JDFDate;

/**
 * Preprocesses a JMF message by replacing JMF/@TimeStamp
 * 
 * @author Claes Buckwalter (clabu@itn.liu.se)
 */
public class TimeStampPreprocessor implements Preprocessor {

    private static Log log = LogFactory.getLog(TimeStampPreprocessor.class);

    public TimeStampPreprocessor() {
    }

    /**
     * Preprocesses a JMF message by replacing JMF/@TimeStamp
     */
	public Message preprocess(Message message, PreprocessorContext context) throws PreprocessorException {
        if (!message.getContentType().startsWith(JDFConstants.JMF_CONTENT_TYPE)) {
            log
                    .debug("Message not preprocessed because it did not contain JMF. Content-type was: "
                            + message.getContentType());
            return message;
        }
        if (log.isDebugEnabled()) {
            log.debug("Preprocessor input: " + message.toString());
        }

        JDFJMF jmf = message.getBodyAsJMF();
        jmf.setTimeStamp(new JDFDate());
        message.setBody(jmf.toXML());

        if (log.isDebugEnabled()) {
            log.debug("Preprocessor output: " + message);
        }
        return message;
    }

	public Message preprocess(Message message) throws PreprocessorException {
		return preprocess(message, null);
	}
}