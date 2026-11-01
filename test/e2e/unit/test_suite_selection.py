"""Suite selection preserves real IPC defaults and the host-owned run marker."""
from pathlib import Path
import os
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import AsyncMock, Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'scripts'))
from android_e2e import Runner, main


class SuiteSelectionTest(unittest.IsolatedAsyncioTestCase):
    async def test_known_suites_select_only_the_requested_class_and_keep_real_model_arguments(self):
        with tempfile.TemporaryDirectory(prefix='divedeep-suite-') as directory:
            root = Path(directory)
            for suite, test_class in (
                ('overlay', 'OverlayLlmdIntegrationTest'), ('disconnect', 'OverlayLlmdDisconnectTest'),
            ):
                with self.subTest(suite=suite):
                    runner = Runner(SimpleNamespace(
                        device='synthetic', adb='unused', timeout=30, suite=suite, llmd_variant='debug',
                        state_dir=root / 'private', report_dir=root / suite,
                    ))
                    runner.device = SimpleNamespace(shell=AsyncMock())
                    runner.install = AsyncMock()
                    runner.gradle = AsyncMock()
                    transaction = SimpleNamespace(run_id='synthetic-run', save=Mock())
                    await runner.tests(transaction)
                    arguments = runner.gradle.call_args.args[0]
                    self.assertIn('-Pandroid.testInstrumentationRunnerArguments.class=' +
                                  'com.storyteller_f.divedeep.' + test_class, arguments)
                    self.assertIn('-Pandroid.testInstrumentationRunnerArguments.realLlmd=true', arguments)
                    self.assertIn('-Pandroid.testInstrumentationRunnerArguments.hostRunId=synthetic-run', arguments)
                    self.assertIn('-Pandroid.testInstrumentationRunnerArguments.llmdVariant=debug', arguments)


class SuiteDefaultTest(unittest.TestCase):
    def test_cli_keeps_overlay_as_the_default(self):
        with patch.dict(os.environ), patch.object(sys, 'argv', [
            'android_e2e.py', '--device', 'synthetic', '--llmd-variant', 'debug',
        ]), patch('android_e2e.signal.signal'), patch('android_e2e.Runner') as runner:
            runner.return_value.execute = AsyncMock(return_value=0)
            self.assertEqual(main(), 0)
            self.assertEqual(runner.call_args.args[0].suite, 'overlay')
