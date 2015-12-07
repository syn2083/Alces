package org.cip4.tools.alces.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.mail.Multipart;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.Logger;
import org.cip4.jdflib.core.JDFDoc;
import org.cip4.jdflib.core.JDFParser;
import org.cip4.jdflib.node.JDFNode;
import org.cip4.jdflib.util.MimeUtil;
import org.cip4.tools.alces.jmf.JMFMessageBuilder;
import org.cip4.tools.alces.message.InMessage;
import org.cip4.tools.alces.message.OutMessage;
import org.cip4.tools.alces.preprocessor.PreprocessorContext;
import org.cip4.tools.alces.preprocessor.jdf.JDFPreprocessor;
import org.cip4.tools.alces.preprocessor.jdf.JobIDPreprocessor;
import org.cip4.tools.alces.preprocessor.jdf.NodeInfoPreprocessor;
import org.cip4.tools.alces.preprocessor.jdf.UrlResolvingPreprocessor;
import org.cip4.tools.alces.preprocessor.jmf.Preprocessor;
import org.cip4.tools.alces.preprocessor.jmf.SenderIDPreprocessor;
import org.cip4.tools.alces.preprocessor.jmf.URLPreprocessor;
import org.cip4.tools.alces.transport.HttpDispatcher;
import org.cip4.tools.alces.transport.HttpReceiver;
import org.cip4.tools.alces.util.ConfigurationHandler;
import org.cip4.tools.alces.util.JDFConstants;
import org.cip4.tools.alces.util.NotDirFilter;

/**
 * This is the class in the Alces framework that is responsible for creating test sessions during which JMF messages are sent, received, and tested. Example
 * usage:
 * <ol>
 * <li>Create a TestRunner, {@link #TestRunner()}.</li>
 * <li>Initialize the test environment {@link #init()}. This starts a HTTP server, etc.</li>
 * <li>Start a {@link TestSession} using one of the <code>startTestSession*</code>-methods.</li>
 * <li>Any incoming messages that Alces can associate with the outgoing message that started a <code>TestSession</code> will automatically be associated with
 * the <code>TestSession</code>. To receive events when an incoming message is received, register a listener with the <code>TestSession</code> using
 * {@link TestSession#addListener(TestSessionListener)}</li>
 * <li>When finished, destroy the test environment to release resources, {@link #destroy()}</li>
 * </ol>
 * 
 * @author Claes Buckwalter (clabu@itn.liu.se)
 * @version $Id: TestRunner.java,v 1.3 2008/05/26 08:35:07 okhilov Exp $
 */
public class TestRunner {

	private static Logger LOGGER = Logger.getLogger(TestRunner.class);

	private TestSuite _suite = null;

	private HttpReceiver _httpReceiver = null;

	private HttpDispatcher _dispatcher = null;

	private ConfigurationHandler _confHand = null;

	/**
	 * Default constructor that uses the {@link TestSuite} implemenation {@link TestSuiteImpl}.
	 */
	public TestRunner() {
		this(new TestSuiteImpl());
	}

	/**
	 * Creates a new test environment for running tests. The {@link TestSuite} is the data model used to store all test data that is generated during the test
	 * sessions ({@link TestSession}).
	 * 
	 * @param testSuite the TestSuite implementation to which all test data will generated by the TestRunner will be added
	 * @see TestSuite
	 * @see TestSession
	 */
	public TestRunner(TestSuite testSuite) {
		this(testSuite, ConfigurationHandler.getInstance());
	}

	public TestRunner(TestSuite testSuite, ConfigurationHandler config) {
		_suite = testSuite;
		_confHand = config;
	}

	/**
	 * Runs a suite of tests.
	 * 
	 * @param targetUrl the URL to send the test data to
	 * @param testDataDir the directory containing the test data
	 * @param props the configuration properties
	 * @return the XML file containing the test suite's results
	 * @throws Exception
	 */
	public void runTests(String targetUrl, String testDataDir) throws Exception {
		// Loads test files
		File[] testFiles = loadTestData(testDataDir);

		LOGGER.info("Running tests...");
		int testDelay = Integer.parseInt(_confHand.getProp(ConfigurationHandler.SEND_DELAY));
		for (int i = 0; i < testFiles.length; i++) {
			LOGGER.info("Posting '" + testFiles[i].getAbsolutePath() + "' to " + targetUrl + "...");
			startTestSession(testFiles[i], targetUrl);
			try {
				// Sleep between tests
				Thread.sleep(testDelay);
			} catch (InterruptedException ie) {
			}
		}
		// Wait for asynchronous messages for the entire test duration
		try {
			int sleepTime = Integer.parseInt(_confHand.getProp(ConfigurationHandler.SESSION_DURATION));
			LOGGER.info("Waiting " + sleepTime + " millis for incoming messages...");
			Thread.sleep(sleepTime);
		} catch (InterruptedException ie) {
		}
		LOGGER.info("Finished running tests.");
	}

	/**
	 * Convenience message for starting a test session.
	 * 
	 * @param testFile the path to a file containing a message that will start the test session
	 * @param targetUrl the URL to send the message to
	 * @return the started test session
	 * @see #startTestSession(File, String)
	 */
	public TestSession startTestSession(String testFile, String targetUrl) {
		return startTestSession(new File(testFile), targetUrl);
	}

	public TestSession startTestSession(OutMessage message, String targetUrl) {
		return startTestSession(message, null, new PreprocessorContext(), targetUrl);
	}

	/**
	 * Starts a new test session using the specified file as the test session's initiating outgoing message.
	 * 
	 * All outgoing messages sent and incoming messages received during a test session are tested by the {@link org.cip4.tools.alces.test.tests.Test}s
	 * configured for this <code>TestRunner</code>.
	 * 
	 * All outgoing messages are preprocessed by the {@link org.cip4.tools.alces.preprocessor.jmf.Preprocessor}s configured for this <code>TestRunner</code>.
	 * 
	 * @param testFile a file containing the message that starts the test session, for example a JMF message or a MIME package
	 * @param targetUrl the URL to send the outgoing message to
	 * @return the started test session
	 */
	public TestSession startTestSession(File testFile, String targetUrl) {
		return startTestSession(loadMessage(testFile), null, new PreprocessorContext(), targetUrl);
	}

	/**
	 * Starts a <code>TestSession</code> based on the specified outgoing message.
	 * 
	 * @param outMessage the outgoing message that starts the test session
	 * @param jdfFile the JDF file submitted by the message; <code>null</code> if no JDF file was submitted
	 * @param context the context used when preprocessing the message and the JDF file
	 * @param targetUrl the URL to send the outgoing message to
	 * @return the started test session
	 */
	public TestSession startTestSession(OutMessage outMessage, File jdfFile, PreprocessorContext context, String targetUrl) {
		return startTestSession(outMessage, jdfFile, context, targetUrl, false);
	}

	/**
	 * Starts a new <code>TestSession</code> by submitting a JDF file.
	 * 
	 * @param outMessage the message used to submit the JDF file
	 * @param jdfFile the JDF file to submit
	 * @param context the preprocessing context
	 * @param targetUrl the URL to submit the JDF file to
	 * @param asMime <code>true</code> to package the JDF file, its content files, and the SubmitQueueEntry JMF message in a MIME package
	 * @return
	 */
	public TestSession startTestSession(OutMessage outMessage, File jdfFile, PreprocessorContext context, String targetUrl, boolean asMime) {
		// Preprocess message
		context.addAttribute(SenderIDPreprocessor.SENDERID_ATTR, ConfigurationHandler.getSenderId());
		context.addAttribute(URLPreprocessor.URL_ATTR, _confHand.getServerJmfUrl());
		boolean mjmDetected = outMessage.getContentType().startsWith(JDFConstants.MIME_CONTENT_TYPE);
		if (mjmDetected) {
			outMessage = preprocessMIME(outMessage, context);
		} else {
			outMessage = preprocessJMF(outMessage, context);
		}
		// Preprocess JDF
		if (jdfFile != null) {
			context.addAttribute(NodeInfoPreprocessor.SUBSCRIPTION_URL_ATTR, _confHand.getServerJmfUrl());
			context.addAttribute(NodeInfoPreprocessor.MESSAGEID_PREFIX_ATTR, outMessage.getBodyAsJMF().getMessageElement(null, null, 0).getID());
			preprocessJDF(jdfFile, context);
		}
		if (asMime) {
			outMessage = packageAsMime(outMessage, jdfFile);
		}

		// Configure and start test session
		synchronized (_suite) {
			LOGGER.debug("Configuring test session...");
			final TestSession session = _suite.createTestSession(targetUrl);
			_suite.addTestSession(session);
			// Configure tests
			_confHand.configureIncomingTests(session);
			_confHand.configureOutgoingTests(session);
			// Send message
			LOGGER.debug("Starting test session and sending message...");
			session.sendMessage(outMessage, _dispatcher);
			return session;
		}
	}

	/**
	 * Packages an the JMF message contained in <code>outMessage</code>, the JDF file, and all files referenced by the JDF file in a MIME package that is
	 * wrapped in an <code>OutMessage</code>.
	 * 
	 * @param outMessage the JMF message to package together with the JDF file
	 * @param jdfFile the JDF file to package
	 * @return an OutMessage which body is the MIME package
	 * @todo Refactor! We really need a smarter base implementation of Message that does no hold the entire message in memory. MIME packages can be huge...
	 */
	public OutMessage packageAsMime(OutMessage outMessage, File jdfFile) {
		JDFDoc jdfDoc = new JDFParser().parseFile(jdfFile.getAbsolutePath());
		if (jdfFile instanceof PublishedFile) {
			jdfDoc.setOriginalFileName(((PublishedFile) jdfFile).getOriginalFile().getAbsolutePath());
		}
		JDFDoc jmfDoc = new JDFDoc(outMessage.getBodyAsJMF().getOwnerDocument());
		jmfDoc.setOriginalFileName("message.jmf");
		Multipart multipart = MimeUtil.buildMimePackage(jmfDoc, jdfDoc);
		OutMessage mimeMessage = null;
		try {
			File mjmFile = File.createTempFile("alces", ".mjm");
			mjmFile.deleteOnExit();
			MimeUtil.writeToFile(multipart, mjmFile.getAbsolutePath()); // XXX
			// Check
			// for
			// null
			mimeMessage = loadMessage(mjmFile);
		} catch (IOException e) {
			LOGGER.error("Could not write MIME package: " + e.getMessage(), e);
		}
		return mimeMessage;
	}

	/**
	 * Configures the message dispatcher.
	 * 
	 * @return
	 */
	private HttpDispatcher configureHttpDispatcher() {
		int maxConnections;
		try {
			maxConnections = Integer.parseInt(_confHand.getProp(ConfigurationHandler.OUTGOING_CONNECTIONS));
		} catch (NumberFormatException nfe) {
			maxConnections = -1;
		}
		final HttpDispatcher dispatcher;
		if (Boolean.parseBoolean(_confHand.getProp(ConfigurationHandler.PROXY_ENABLED))) {
			// Dispatcher that uses a proxy
			int proxyPort;
			try {
				proxyPort = Integer.parseInt(_confHand.getProp(ConfigurationHandler.PROXY_PORT));
			} catch (NumberFormatException nfe) {
				proxyPort = -1;
			}
			String proxyHost = _confHand.getProp(ConfigurationHandler.PROXY_HOST);
			LOGGER.info("Using proxy: " + proxyHost + ":" + proxyPort);
			dispatcher = new HttpDispatcher(proxyHost, proxyPort, _confHand.getProp(ConfigurationHandler.PROXY_USER), _confHand.getProp(ConfigurationHandler.PROXY_PASSWORD), maxConnections,
					maxConnections);
		} else {
			// Dispatcher without a proxy
			dispatcher = new HttpDispatcher(maxConnections, maxConnections);
		}
		return dispatcher;
	}

	/**
	 * Loads Alces test data, the JMF messages and JDF instances to be sent.
	 * 
	 * @param testdataPath the path to a test file or directory containing test files to load the test data from
	 */
	private File[] loadTestData(String testdataPath) {
		File testDir = new File(testdataPath);
		LOGGER.info("Loading test data from '" + testDir.getAbsolutePath() + "'...");
		final File[] testFiles;
		// Checks that the directory exists
		if (testDir.isDirectory()) {
			testFiles = testDir.listFiles(new NotDirFilter());
		} else {
			testFiles = new File[] { new File(testdataPath) };
		}
		LOGGER.info("Test data loaded (" + testFiles.length + " files).");
		return testFiles;
	}

	/**
	 * Initializes the test environment:
	 * <ul>
	 * <li>Creates and start HTTP server that listens for incoming messages</li>
	 * <li>Creates a HTTP dispatcher for sending outgoing messages</li>
	 * </ul>
	 * 
	 * @throws Exception if the test session could not be initialized
	 * @see #destroy()
	 */
	public void init() throws Exception {
		LOGGER.debug("Initializing test environment...");
		try {
			// Configure web server for receiving messages
			final String url = _confHand.getServerJmfUrl();
			final String host = _confHand.getServerHost();
			final int port = _confHand.getServerPort();
			final String contextPath = _confHand.getServerJmfContextPath();
			LOGGER.info("Starting HTTP server on " + url + "...");
			final String resourceBase = _confHand.getProp(ConfigurationHandler.RESOURCE_BASE);
			_httpReceiver = new HttpReceiver(_suite);
			_httpReceiver.startServer(host, port, contextPath, resourceBase);
			// Configure message dispatcher
			_dispatcher = configureHttpDispatcher();
			LOGGER.debug("Test environment initialized.");
		} catch (Exception e) {
			LOGGER.error("Could not initialize test environment.", e);
			try {
				_httpReceiver.stopServer();
			} catch (InterruptedException e1) {
				LOGGER.warn("Could not stop HTTP receiver while aborting test environment initialization.", e1);
			}
			_httpReceiver = null;
			_dispatcher = null;
			throw e;
		}
	}

	/**
	 * Releases all resources used by the test environment.
	 * 
	 * @see #init()
	 */
	public void destroy() throws Exception {
		LOGGER.debug("Cleaning up test environment...");
		if (_httpReceiver != null) {
			_httpReceiver.stopServer();
			_httpReceiver = null;
		}
		_dispatcher = null;
		LOGGER.debug("Cleaned up test environment.");
	}

	/**
	 * Sends a <code>OutMessage</code> to the preconfigured target URL. The message is preprocessed before it is sent.
	 * 
	 * If <code>SenderIDPreprocessor</code> is enabled then <em>JMF/@SenderID</em> will be replaced with the value configured SenderID.
	 * 
	 * @param message the <code>InMessage</code> received in the response
	 * @return
	 * @throws IOException if an communication exception occurs during the message sending
	 */
	public InMessage sendMessage(OutMessage message, String targetUrl) throws IOException {
		PreprocessorContext context = new PreprocessorContext();
		context.addAttribute(SenderIDPreprocessor.SENDERID_ATTR, ConfigurationHandler.getSenderId());
		boolean mjmDetected = message.getContentType().startsWith(JDFConstants.MIME_CONTENT_TYPE);
		if (mjmDetected) {
			preprocessMIME(message, context);
		} else {
			preprocessJMF(message, context);
		}
		return _dispatcher.dispatch(message, targetUrl);
	}

	/**
	 * Loads a message from a file.
	 * 
	 * @param file
	 * @return
	 */
	public OutMessage loadMessage(File file) {
		LOGGER.debug("Loading message from file '" + file.getAbsolutePath() + "'...");
		String contentType = null;
		String header = null;
		String body = null;
		try {
			if (file.getName().endsWith(JDFConstants.JMF_EXTENSION)) {
				contentType = JDFConstants.JMF_CONTENT_TYPE;
			} else if (file.getName().endsWith(JDFConstants.JDF_EXTENSION)) {
				contentType = JDFConstants.JDF_CONTENT_TYPE;
			} else if (file.getName().endsWith(JDFConstants.JMF_MIME_EXTENSION)) {
				contentType = JDFConstants.MIME_CONTENT_TYPE;
			} else if (file.getName().endsWith(JDFConstants.JDF_MIME_EXTENSION)) {
				contentType = JDFConstants.MIME_CONTENT_TYPE;
			} else if (file.getName().endsWith(JDFConstants.XML_EXTENSION)) {
				contentType = JDFConstants.XML_CONTENT_TYPE;
			} else {
				contentType = "text/plain";
			}
			body = IOUtils.toString(new FileInputStream(file));
		} catch (IOException ioe) {
			LOGGER.error("Could not send message because it could not be loaded from file.", ioe);
			return null;
		}
		LOGGER.debug("Loaded message from file.");

		OutMessage message = _suite.createOutMessage(contentType, header, body, true); // new
		// OutMessageImpl(contentType,
		// header,
		// body,
		// true);
		return message;
	}

	/**
	 * Starts a new test session by resubmitting a JDF file. Any relative URLs in the JDF file will be resolved against the JDF file and replaced with http
	 * URLs.
	 * 
	 * @param jdfFile the JDF file to submit
	 * @param targetUrl the URL to submit it to
	 * @param preprocessJdf <code>true</code> to preprocess the JDF file before submitting it; <code>false</code> otherwise
	 * @return the test session
	 * @see #startTestSessionWithResubmitQueueEntry(File, String, String, String)
	 * @see #publishJDFWithFileUrls(File, String)
	 */
	public TestSession startTestSessionWithSubmitQueueEntryWithHttpUrls(File jdfFile, String targetUrl, boolean preprocessJdf) {
		return startTestSessionWithSubmitQueueEntry(jdfFile, targetUrl, preprocessJdf, false, getPublishedJDFBaseURL());
	}

	/**
	 * Starts a new test session by resubmitting a JDF file. Any relative URLs in the JDF file will be resolved against the JDF file and replaced with absolute
	 * file URLs.
	 * 
	 * @param jdfFile the JDF file to submit
	 * @param targetUrl the URL to submit it to
	 * @param preprocessJdf if <code>true</code> the JDF file is preprocessed before sent
	 * @return the test session
	 * @see #startTestSessionWithResubmitQueueEntry(File, String, String, String)
	 * @see #publishJDFWithFileUrls(File, String)
	 */
	public TestSession startTestSessionWithSubmitQueueEntryWithFileUrls(File jdfFile, String targetUrl, boolean preprocessJdf) {
		return startTestSessionWithSubmitQueueEntry(jdfFile, targetUrl, preprocessJdf, false, jdfFile.getParentFile().toURI().toASCIIString());
	}

	/**
	 * Starts a new test session by sending a SubmitQueueEntry JMF message that refers to the specified JDF file. Before submitting the JDF job, the JDF file is
	 * published to this TestRunners HTTP server and the resulting http URL is used in the SubmitQueueEntry JMF message.
	 * 
	 * @param jdfFile the JDF file to submit
	 * @param targetUrl the URL to submit it to
	 * @param preprocessJdf <code>true</code> to preprocess the JDF file before submitting it; <code>false</code> otherwise
	 * @param asMime packages the JDF file, its content files, and the SubmitQueueEntry JMF in a MIME package
	 * @param baseUrl If the JDF file references any files using relative URLs then these will be resolved against this base URL. <code>null</code> to not
	 * resolve any URLs.
	 * @return the test session
	 */
	public TestSession startTestSessionWithSubmitQueueEntry(File jdfFile, String targetUrl, boolean preprocessJdf, boolean asMime, String baseUrl) {
		jdfFile = publishJDF(jdfFile);
		PreprocessorContext context = new PreprocessorContext();
		context.addAttribute(JDFPreprocessor.PREPROCESSING_ENABLED, Boolean.toString(preprocessJdf));
		context.addAttribute(UrlResolvingPreprocessor.BASEURL_ATTR, baseUrl);
		// String jdfUrl = publishJDF(jdfFile, replaceJobId, baseUrl);
		OutMessage message = JMFMessageBuilder.buildSubmitQueueEntry(resolvePublishedJDF(jdfFile));
		return startTestSession(message, jdfFile, context, targetUrl, asMime);
	}

	/**
	 * Starts a new test session by sending a SubmitQueueEntry JMF message that refers to the specified JDF file. Before submitting the JDF job, the JDF file is
	 * published to this TestRunners HTTP server and the resulting http URL is used in the SubmitQueueEntry JMF message.
	 * 
	 * @param jdfFile the JDF file to submit
	 * @param targetUrl the URL to submit it to
	 * @param preprocessJdf <code>true</code> to preprocess the JDF file before submitting it; <code>false</code> otherwise
	 * @param asMime packages the JDF file, its content files, and the SubmitQueueEntry JMF in a MIME package
	 * 
	 * @return the test session
	 */
	public TestSession startTestSessionWithSubmitQueueEntry(File jdfFile, String targetUrl, boolean preprocessJdf, boolean asMime) {
		final String baseUrl;
		final String replaceUrls = _confHand.getProp(ConfigurationHandler.REPLACE_URLS_IN_JDF);
		if (replaceUrls.equals(ConfigurationHandler.REPLACE_URLS_IN_JDF_WITH_HTTP)) {
			baseUrl = getPublishedJDFBaseURL();
		} else if (replaceUrls.equals(ConfigurationHandler.REPLACE_URLS_IN_JDF_WITH_FILE)) {
			baseUrl = jdfFile.getParentFile().toURI().toASCIIString();
		} else if (replaceUrls.equals(ConfigurationHandler.REPLACE_URLS_IN_JDF_DISABLED)) {
			baseUrl = null;
		} else {
			LOGGER.warn("Unknown configuration option for replacing relative URLs in JDF files. " + "Using property value as base URL: " + replaceUrls);
			baseUrl = replaceUrls;
		}
		return startTestSessionWithSubmitQueueEntry(jdfFile, targetUrl, preprocessJdf, asMime, baseUrl);
	}

	/**
	 * Starts a new test session by resubmitting a JDF file. Any relative in the JDF file will be resolved against the JDF file and replaced with http URLs.
	 * 
	 * @param jdfFile the JDF file to submit
	 * @param queueEntryId the queue entry ID of the job to resubmit
	 * @param jobId the /JDF/@JobID of the job to resubmit
	 * @param targetUrl the URL to submit it to
	 * @return the test session
	 * @see #startTestSessionWithResubmitQueueEntry(File, String, String, String)
	 * @see #publishJDFWithHttpUrls(File, String)
	 */
	public TestSession startTestSessionWithResubmitQueueEntryWithHttpUrls(File jdfFile, String queueEntryId, String jobId, String targetUrl) {
		return startTestSessionWithResubmitQueueEntry(jdfFile, queueEntryId, jobId, targetUrl, getPublishedJDFBaseURL(), true, false);
	}

	/**
	 * Starts a new test session by resubmitting a JDF file. Any relative in the JDF file will be resolved against the JDF file and replaced with absolute file
	 * URLs.
	 * 
	 * @param jdfFile the JDF file to submit
	 * @param queueEntryId the queue entry ID of the job to resubmit
	 * @param jobId the /JDF/@JobID of the job to resubmit
	 * @param targetUrl the URL to submit it to
	 * @return the test session
	 * @see #startTestSessionWithResubmitQueueEntry(File, String, String, String)
	 * @see #publishJDFWithFileUrls(File, String)
	 */
	public TestSession startTestSessionWithResubmitQueueEntryWithFileUrls(File jdfFile, String queueEntryId, String jobId, String targetUrl) {
		return startTestSessionWithResubmitQueueEntry(jdfFile, queueEntryId, jobId, targetUrl, jdfFile.getAbsolutePath(), true, false);
	}

	/**
	 * Starts a new test session by sending a <i>ResubmitQueueEntry</i> JMF message. that refers to the specified JDF file.
	 * 
	 * If <code>asMime</code> is <code>true</code> the JDF file, its content files, and the <i>ResubmitQueueEntry</i> JMF message are bundled in a MIME package.
	 * 
	 * If <code>asMime</code> is <code>false</code>, the JDF job, the JDF file is published to this TestRunners HTTP server and the resulting http URL is used
	 * in the <i>ResubmitQueueEntry</i> JMF message. Any relative URLs in the JDF file will be resolved against <code>baseUrl</code> and replaced with an
	 * absolute URL, see {@link #publishJDF(File, String, String)}.
	 * 
	 * @param jdfFile the JDF file to submit
	 * @param queueEntryId the queue entry ID of the job to resubmit
	 * @param jobId the /JDF/@JobID of the job to resubmit
	 * @param targetUrl the URL to submit it to
	 * @param baseUrl If the JDF file references any files using relative URLs then these will be resolved against this base URL. <code>null</code> to not
	 * resolve and replace any URLs.
	 * @param preprocessJdf
	 * @param asMime packages the JDF file, its content files, and the SubmitQueueEntry JMF in a MIME package
	 * @return the test session
	 */
	public TestSession startTestSessionWithResubmitQueueEntry(File jdfFile, String queueEntryId, String jobId, String targetUrl, String baseUrl, boolean preprocessJdf, boolean asMime) {
		jdfFile = publishJDF(jdfFile);
		PreprocessorContext context = new PreprocessorContext();
		context.addAttribute(JDFPreprocessor.PREPROCESSING_ENABLED, Boolean.toString(preprocessJdf));
		context.addAttribute(JobIDPreprocessor.JOBID_ATTR, jobId);
		context.addAttribute(UrlResolvingPreprocessor.BASEURL_ATTR, baseUrl);
		OutMessage message = JMFMessageBuilder.buildResubmitQueueEntry(resolvePublishedJDF(jdfFile), queueEntryId);
		return startTestSession(message, jdfFile, context, targetUrl, asMime);
	}

	/**
	 * Starts a new test session by sending a <i>ResubmitQueueEntry</i> JMF message. that refers to the specified JDF file.
	 * 
	 * If <code>asMime</code> is <code>true</code> the JDF file, its content files, and the <i>ResubmitQueueEntry</i> JMF message are bundled in a MIME package.
	 * 
	 * If <code>asMime</code> is <code>false</code>, the JDF job, the JDF file is published to this TestRunners HTTP server and the resulting http URL is used
	 * in the <i>ResubmitQueueEntry</i> JMF message.
	 * 
	 * @param jdfFile the JDF file to submit
	 * @param queueEntryId the queue entry ID of the job to resubmit
	 * @param jobId the /JDF/@JobID of the job to resubmit
	 * @param targetUrl the URL to submit it to
	 * @param preprocessJdf
	 * @param asMime packages the JDF file, its content files, and the SubmitQueueEntry JMF in a MIME package
	 * @return the test session
	 */
	public TestSession startTestSessionWithResubmitQueueEntry(File jdfFile, String queueEntryId, String jobId, String targetUrl, boolean preprocessJdf, boolean asMime) {
		final String baseUrl;
		final String replaceUrls = _confHand.getProp(ConfigurationHandler.REPLACE_URLS_IN_JDF);
		if (replaceUrls.equals(ConfigurationHandler.REPLACE_URLS_IN_JDF_WITH_HTTP)) {
			baseUrl = getPublishedJDFBaseURL();
		} else if (replaceUrls.equals(ConfigurationHandler.REPLACE_URLS_IN_JDF_WITH_FILE)) {
			baseUrl = jdfFile.getParentFile().toURI().toASCIIString();
		} else if (replaceUrls.equals(ConfigurationHandler.REPLACE_URLS_IN_JDF_DISABLED)) {
			baseUrl = null;
		} else {
			LOGGER.warn("Unknown configuration option for replacing relative URLs in JDF files. " + "Using property value as base URL: " + replaceUrls);
			baseUrl = replaceUrls;
		}
		return startTestSessionWithResubmitQueueEntry(jdfFile, queueEntryId, jobId, targetUrl, baseUrl, preprocessJdf, asMime);
	}

	/**
	 * Publishes a JDF file to Alces's HTTP server's public directory.
	 * 
	 * @param jdfFile the JDF file to publish
	 * @return the published JDF file
	 */
	public File publishJDF(File jdfFile) {
		LOGGER.debug("Publishing JDF '" + jdfFile.getAbsolutePath() + "' to web server ...");
		if (!jdfFile.exists()) {
			throw new IllegalArgumentException("The JDF file '" + jdfFile.getAbsolutePath() + "' cannot be published because the file does not exist.");
		}
		String publicDirPath = _confHand.getProp(ConfigurationHandler.RESOURCE_BASE);
		// Create JDF dir
		File publicJdfDir = new File(publicDirPath, "jdf");
		publicJdfDir.mkdir();
		// Create public JDF filename
		String jdfFilename = RandomStringUtils.randomAlphanumeric(16) + ".jdf";
		File publicJdfFile = new PublishedFile(publicJdfDir, jdfFilename, jdfFile);
		try {
			FileUtils.copyFile(jdfFile, publicJdfFile);
		} catch (IOException e) {
			LOGGER.error("The JDF file '" + jdfFile + "' could not be published to '" + publicJdfFile + "'.", e);
		}
		return publicJdfFile;
	}

	/**
	 * Returns the HTTP URL of the directory where JDF files are published so that they can be downloaded.
	 * 
	 * @return the HTTP URL, ending with a slash character, where JDF files are published
	 */
	private String getPublishedJDFBaseURL() {
		String publicJdfUrl = null;
		String host = _confHand.getProp(ConfigurationHandler.HOST);
		int port = Integer.parseInt(_confHand.getProp(ConfigurationHandler.PORT));
		publicJdfUrl = "http://" + host + ":" + port + "/jdf/";
		return publicJdfUrl;
	}

	/**
	 * Resolves a published JDF file to a HTTP URL.
	 * 
	 * @param jdfFile
	 * @return a HTTP URL
	 */
	private String resolvePublishedJDF(File jdfFile) {
		return getPublishedJDFBaseURL() + jdfFile.getName();
	}

	/**
	 * Preprocesses a MIME package. This methods implementation currently has the following limitations:
	 * <ul>
	 * <li>Only the JMF is preprocessed, not the JDF</li>
	 * <li>Content files in the original MIME package are not included in the new MIME package returned</li>
	 * </ul>
	 * 
	 * @param message
	 * @param context
	 * @return
	 * @author Alex Khilov
	 * @since 0.9.9.3
	 */
	private OutMessage preprocessMIME(OutMessage message, PreprocessorContext context) {
		String mjmEnableStr = ConfigurationHandler.getInstance().getProp(ConfigurationHandler.MJM_MIME_FILE_PARSE);
		boolean mjmEnabled = new Boolean(mjmEnableStr);
		// 3 steps are here: separate MIME message, preprocess JMF part, glue it back.
		if (mjmEnabled) {
			// take JMF part from MIME
			OutMessage jmfMessage = (OutMessage) TestSessionImpl.getJMFFromMime(message);
			// preprocess JMF part
			jmfMessage = preprocessJMF(jmfMessage, context);
			// glue it back
			JDFDoc jdfDoc = new JDFParser().parseFile(TestSessionImpl.getJDFFileFromMime(message));
			JDFDoc jmfDoc = new JDFDoc(jmfMessage.getBodyAsJMF().getOwnerDocument());
			Multipart multipart = MimeUtil.buildMimePackage(jmfDoc, jdfDoc);
			try {
				File mjmFile = File.createTempFile("alces-PACKAGE-PREPARING-", ".mjm");
				mjmFile.deleteOnExit();
				MimeUtil.writeToFile(multipart, mjmFile.getAbsolutePath());

				message.setBody(IOUtils.toString(new FileInputStream(mjmFile)));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return message;
	}

	/**
	 * Applies all preprocessors in the List to the Message. Preprocessors are applied in the order they occur in the list. If a preprocessor fails it does so
	 * quietly and preprocessing continues.
	 * 
	 * @param preprocessors
	 * @param message
	 */
	private OutMessage preprocessJMF(OutMessage message, PreprocessorContext context) {
		Preprocessor[] preprocessors = _confHand.getJMFPreprocessors();
		LOGGER.debug("Preprocessing message with " + preprocessors.length + " preprocessors...");
		for (int i = 0; i < preprocessors.length; i++) {
			try {
				preprocessors[i].preprocess(message, context);
			} catch (Exception e) {
				LOGGER.warn("Preprocessing message failed.", e);
			}
		}
		return message;
	}

	/**
	 * Preprocesses the JDF file with the currently configured preprocessors. The file is read from disk, preprocessed, and then written back to the same file.
	 * If a preprocessor fails it does so quietly and preprocessing continues. If the file could not be parsed a warning is logged and <code>null</code> is
	 * returned.
	 * 
	 * @param jdfFile the JDF file to preprocess
	 * @param context the preprocessing context
	 * @return the preprocessed JDF node that was written back to the file; <code>null</code> if the JDF file could not be parsed
	 */
	private JDFNode preprocessJDF(File jdfFile, PreprocessorContext context) {

		JDFDoc jdfDoc = new JDFParser().parseFile(jdfFile.getAbsolutePath());
		if (jdfDoc == null) {
			LOGGER.warn("Could not parse JDF file '" + jdfFile.getAbsolutePath() + "' for preprocessing.");
			return null;
		}
		JDFNode jdf = preprocessJDF(jdfDoc.getJDFRoot(), context);
		if (!jdf.getOwnerDocument_KElement().write2File(jdfFile.getAbsolutePath(), 2, true)) {
			LOGGER.warn("Could not write preprocessed JDF back to file '" + jdfFile.getAbsolutePath() + "'.");
			return null;
		}
		return jdf;
	}

	/**
	 * Preprocesses the JDF node with the currently configured preprocessors. If a preprocessor fails it does so quietly and preprocessing continues.
	 * 
	 * @param jdf the JDF node to preprocess
	 * @param context the preprocessing context
	 */
	private JDFNode preprocessJDF(JDFNode jdf, PreprocessorContext context) {
		if (context.getAttribute(JDFPreprocessor.PREPROCESSING_ENABLED).equals("false")) {
			return jdf;
		}
		JDFPreprocessor[] preprocessors = _confHand.getJDFPreprocessors();
		LOGGER.debug("Preprocessing JDF with " + preprocessors.length + " preprocessors...");
		for (int i = 0; i < preprocessors.length; i++) {
			try {
				preprocessors[i].preprocess(jdf, context);
			} catch (Exception e) {
				LOGGER.warn("Preprocessing message failed.", e);
			}
		}
		return jdf;
	}

	/**
	 * Serializes the test suite, all incoming and outgoing messages to a directory and creates an XML-based test report file containing a log of all messages
	 * and the test results.
	 * 
	 * @param outputDir the directory to write the test suite to
	 * @return the XML-based test report file
	 * @throws IOException
	 */
	public synchronized String serializeTestSuite(String outputDir) throws IOException {
		TestSuiteSerializer serializer = new TestSuiteSerializer();
		// TODO Synchronize TestSuite while serializing
		return serializer.serialize(_suite, outputDir);
	}

	/**
	 * A wrapper for a published file.
	 * 
	 * @author Claes Buckwalter
	 */
	private class PublishedFile extends File {
		private final File originalFile;

		/**
		 * @param parent the published file's parent path
		 * @param child the published file's name
		 * @param originalFile the original file
		 */
		PublishedFile(String parent, String child, File originalFile) {
			this(new File(parent), child, originalFile);
		}

		PublishedFile(File parent, String child, File originalFile) {
			super(parent, child);
			this.originalFile = originalFile;
		}

		public File getOriginalFile() {
			return originalFile;
		}
	}
}
