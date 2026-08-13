const {
  click,
  createSession,
  deleteSession,
  findByResourceId,
  waitUntil,
} = require('./android-appium');

const LLMD_PACKAGES = {
  release: 'com.storytellerf.llmd',
  daily: 'com.storytellerf.llmd.daily',
  debug: 'com.storytellerf.llmd.debug',
};
const LLMD_VARIANT = process.env.LLMD_VARIANT || 'release';
const LLMD_PACKAGE = LLMD_PACKAGES[LLMD_VARIANT];
const LLMD_AUTH_ACTIVITY = 'com.storytellerf.llmd.LlmdIpcAuthorizationActivity';
const AUTH_ACTION = 'com.storytellerf.llmd.action.AUTHORIZE_CALLER';
const CALLER_PACKAGE = 'com.storyteller_f.divedeep';
const ALLOW_BUTTON_ID = 'com.storytellerf.llmd:id/ipc_authorization_allow';

async function main() {
  if (!LLMD_PACKAGE) {
    throw new Error(`LLMD_VARIANT must be release, daily, or debug: ${LLMD_VARIANT}`);
  }
  const sessionId = await createSession({
    'appium:appPackage': LLMD_PACKAGE,
    'appium:appActivity': LLMD_AUTH_ACTIVITY,
    'appium:intentAction': AUTH_ACTION,
    'appium:optionalIntentArguments': `--es caller_package ${CALLER_PACKAGE}`,
    'appium:noReset': true,
    'appium:forceAppLaunch': true,
    'appium:newCommandTimeout': 120,
  });

  try {
    await waitUntil(
      async () => Boolean(await findByResourceId(sessionId, ALLOW_BUTTON_ID)),
      {
        timeout: 30000,
        interval: 1000,
        timeoutMsg: 'llmd authorization allow button did not appear',
      },
    );
    const allowButton = await findByResourceId(sessionId, ALLOW_BUTTON_ID);
    await click(sessionId, allowButton);
  } finally {
    await deleteSession(sessionId);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
