const ELEMENT_KEY = 'element-6066-11e4-a52e-4f735466cecf';

function appiumBaseUrl() {
  const host = process.env.APPIUM_HOST || '127.0.0.1';
  const port = Number(process.env.APPIUM_PORT || 4723);
  return `http://${host}:${port}`;
}

async function request(sessionId, method, path, body) {
  const response = await fetch(`${appiumBaseUrl()}${sessionId ? `/session/${sessionId}` : ''}${path}`, {
    method,
    headers: body ? { 'content-type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = payload.value?.message || payload.message || response.statusText;
    const error = new Error(`${method} ${path} failed: ${message}`);
    error.status = response.status;
    throw error;
  }
  return payload.value;
}

async function createSession(capabilities) {
  const value = await request(null, 'POST', '/session', {
    capabilities: {
      alwaysMatch: {
        platformName: 'Android',
        'appium:automationName': 'UiAutomator2',
        ...capabilities,
        ...(process.env.DEVICE ? { 'appium:udid': process.env.DEVICE } : {}),
      },
      firstMatch: [{}],
    },
  });
  return value.sessionId;
}

async function deleteSession(sessionId) {
  await request(sessionId, 'DELETE', '', null);
}

async function findByText(sessionId, text) {
  try {
    const element = await request(sessionId, 'POST', '/element', {
      using: '-android uiautomator',
      value: `new UiSelector().text("${text}")`,
    });
    return element[ELEMENT_KEY] || element.ELEMENT;
  } catch (error) {
    if (error.status === 404) {
      return null;
    }
    throw error;
  }
}

async function findByClassName(sessionId, className) {
  try {
    const element = await request(sessionId, 'POST', '/element', {
      using: '-android uiautomator',
      value: `new UiSelector().className("${className}")`,
    });
    return element[ELEMENT_KEY] || element.ELEMENT;
  } catch (error) {
    if (error.status === 404) {
      return null;
    }
    throw error;
  }
}

async function swipeUp(sessionId) {
  await swipeVertically(sessionId, 0.83, 0.2);
}

async function swipeVertically(sessionId, startFraction, endFraction) {
  const rect = await request(sessionId, 'GET', '/window/rect', null);
  await request(sessionId, 'POST', '/actions', {
    actions: [{
      type: 'pointer',
      id: 'finger',
      parameters: { pointerType: 'touch' },
      actions: [
        {
          type: 'pointerMove',
          duration: 0,
          x: Math.round(rect.width * 0.5),
          y: Math.round(rect.height * startFraction),
        },
        { type: 'pointerDown', button: 0 },
        { type: 'pause', duration: 100 },
        {
          type: 'pointerMove',
          duration: 600,
          x: Math.round(rect.width * 0.5),
          y: Math.round(rect.height * endFraction),
        },
        { type: 'pointerUp', button: 0 },
      ],
    }],
  });
}

async function findByResourceId(sessionId, resourceId) {
  try {
    const element = await request(sessionId, 'POST', '/element', {
      using: 'id',
      value: resourceId,
    });
    return element[ELEMENT_KEY] || element.ELEMENT;
  } catch (error) {
    if (error.status === 404) {
      return null;
    }
    throw error;
  }
}

async function click(sessionId, elementId) {
  await request(sessionId, 'POST', `/element/${elementId}/click`, {});
}

async function waitUntil(predicate, { timeout, interval, timeoutMsg }) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await predicate()) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, interval));
  }
  throw new Error(timeoutMsg);
}

async function startRecording(sessionId) {
  await request(sessionId, 'POST', '/session/:sessionId/appium/start_recording_screen', {
    options: {
      videoType: 'mpeg4',
      videoQuality: 'medium',
    },
  });
}

async function stopRecording(sessionId) {
  const result = await request(sessionId, 'POST', '/session/:sessionId/appium/stop_recording_screen', {
    options: {},
  });
  return Buffer.from(result, 'base64');
}

function generateReport(testName, { status, duration }) {
  const fs = require('fs');
  const path = require('path');

  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const reportDir = path.join(__dirname, 'reports', `${testName}-${timestamp}`);
  fs.mkdirSync(reportDir, { recursive: true });

  const report = {
    testName,
    status,
    duration,
    timestamp: new Date().toISOString(),
  };

  fs.writeFileSync(
    path.join(reportDir, 'report.json'),
    JSON.stringify(report, null, 2)
  );

  console.log(`Report generated: ${path.join(reportDir, 'report.json')}`);
  return reportDir;
}

module.exports = {
  click,
  createSession,
  deleteSession,
  findByClassName,
  findByResourceId,
  findByText,
  generateReport,
  startRecording,
  stopRecording,
  swipeUp,
  waitUntil,
};
