const {
  click,
  createSession,
  deleteSession,
  findByText,
  generateReport,
  startRecording,
  stopRecording,
  waitUntil,
} = require('./android-appium');

const APP_PACKAGE = 'com.storyteller_f.divedeep';
const APP_ACTIVITY = '.MainActivity';
const BUTTON_ENABLE = '开始翻译';
const BUTTON_DISABLE = '停止翻译';

function desiredEnabled() {
  const value = process.argv[2];
  if (value === 'true') return true;
  if (value === 'false') return false;
  throw new Error('Usage: node test/e2e/android-toggle.js <true|false>');
}

async function main() {
  const enabled = desiredEnabled();
  const testName = 'android-toggle';
  const startTime = Date.now();
  let sessionId;

  try {
    sessionId = await createSession({
      'appium:appPackage': APP_PACKAGE,
      'appium:appActivity': APP_ACTIVITY,
      'appium:noReset': true,
      'appium:forceAppLaunch': true,
      'appium:newCommandTimeout': 120,
    });

    // Start recording
    await startRecording(sessionId);

    const targetText = enabled ? BUTTON_ENABLE : BUTTON_DISABLE;
    const oppositeText = enabled ? BUTTON_DISABLE : BUTTON_ENABLE;
    await waitUntil(
      async () => Boolean((await findByText(sessionId, targetText)) || (await findByText(sessionId, oppositeText))),
      {
        timeout: 30000,
        interval: 1000,
        timeoutMsg: `DiveDeep toggle button did not appear: ${targetText}`,
      },
    );

    const targetButton = await findByText(sessionId, targetText);
    if (targetButton) {
      await click(sessionId, targetButton);
      await waitUntil(
        async () => Boolean(await findByText(sessionId, oppositeText)),
        {
          timeout: 10000,
          interval: 500,
          timeoutMsg: `DiveDeep toggle did not switch to: ${oppositeText}`,
        },
      );
    }

    // Stop recording and generate report
    const videoData = await stopRecording(sessionId);
    const duration = Date.now() - startTime;
    const reportDir = generateReport(testName, {
      status: 'passed',
      duration,
    });

    // Save video file
    const fs = require('fs');
    const path = require('path');
    fs.writeFileSync(path.join(reportDir, 'recording.mp4'), videoData);

    console.log(`Test passed. Report generated at: ${reportDir}`);
  } catch (error) {
    // Generate failed report
    const duration = Date.now() - startTime;
    generateReport(testName, {
      status: 'failed',
      duration,
    });
    throw error;
  } finally {
    if (sessionId) {
      await deleteSession(sessionId);
    }
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
