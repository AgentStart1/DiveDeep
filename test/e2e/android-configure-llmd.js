const {
  click,
  createSession,
  deleteSession,
  findByText,
  swipeUp,
} = require('./android-appium');

const APP_PACKAGE = 'com.storyteller_f.divedeep';
const APP_ACTIVITY = '.MainActivity';
const TARGET_LABELS = {
  release: 'Release',
  daily: 'Daily',
  debug: 'Debug',
};
const LLMD_VARIANT = process.env.LLMD_VARIANT || 'release';

async function findTextWithScroll(sessionId, text) {
  for (let attempt = 0; attempt < 8; attempt += 1) {
    const element = await findByText(sessionId, text);
    if (element) return element;
    await swipeUp(sessionId);
  }
  return findByText(sessionId, text);
}

async function main() {
  const targetLabel = TARGET_LABELS[LLMD_VARIANT];
  if (!targetLabel) {
    throw new Error(`LLMD_VARIANT must be release, daily, or debug: ${LLMD_VARIANT}`);
  }

  const sessionId = await createSession({
    'appium:appPackage': APP_PACKAGE,
    'appium:appActivity': APP_ACTIVITY,
    'appium:noReset': true,
    'appium:forceAppLaunch': true,
    'appium:newCommandTimeout': 120,
  });

  try {
    const target = await findTextWithScroll(sessionId, targetLabel);
    if (!target) {
      throw new Error(`DiveDeep llmd target option did not appear: ${targetLabel}`);
    }
    await click(sessionId, target);

    const saveText = '保存翻译配置';
    const saveButton = await findTextWithScroll(sessionId, saveText);
    if (!saveButton) {
      throw new Error('DiveDeep save translation config button did not appear');
    }
    await click(sessionId, saveButton);
    await new Promise((resolve) => setTimeout(resolve, 1000));
  } finally {
    await deleteSession(sessionId);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
