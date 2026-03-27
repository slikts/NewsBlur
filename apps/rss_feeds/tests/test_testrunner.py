from unittest.mock import MagicMock, patch

from django.conf import settings
from django.test import SimpleTestCase, override_settings

from utils.testrunner import TestRunner


class Test_TestRunner(SimpleTestCase):
    @override_settings(
        MONGO_DB={"name": "newsblur_test", "host": "127.0.0.1:27017"},
        MONGO_DB_NAME="newsblur",
        MONGODB="prod-client",
        SITE_ID=2,
    )
    @patch("django.contrib.sites.models.Site.objects.get_or_create")
    @patch("utils.testrunner.setup_databases")
    @patch("utils.testrunner.connect")
    @patch("utils.testrunner.disconnect")
    def test_setup_databases_rebinds_default_alias(
        self, mock_disconnect, mock_connect, mock_setup_databases, mock_get_or_create
    ):
        default_connection = MagicMock(name="default_connection")
        test_connection = MagicMock(name="test_connection")
        mock_connect.side_effect = [default_connection, test_connection]
        mock_setup_databases.return_value = ("db-config",)

        runner = TestRunner(verbosity=0, interactive=False)
        result = runner.setup_databases()

        self.assertEqual(result, ("db-config",))
        self.assertEqual(settings.MONGO_DB_NAME, "newsblur_test")
        self.assertIs(settings.MONGODB, default_connection)
        mock_disconnect.assert_any_call(alias="default")
        mock_disconnect.assert_any_call(alias="test")
        mock_connect.assert_any_call(
            "newsblur_test",
            alias="default",
            host="127.0.0.1:27017",
            connect=False,
            unicode_decode_error_handler="ignore",
        )
        mock_connect.assert_any_call(
            "newsblur_test",
            alias="test",
            host="127.0.0.1:27017",
            connect=False,
            unicode_decode_error_handler="ignore",
        )
        mock_setup_databases.assert_called_once_with(0, False)
        mock_get_or_create.assert_called_once_with(
            pk=2, defaults={"domain": "testserver", "name": "Test Server"}
        )

    @override_settings(MONGO_DB={"name": "newsblur_test", "host": "mongo:27017"})
    @patch("django.test.runner.DiscoverRunner.teardown_databases")
    @patch("utils.testrunner.disconnect_all")
    @patch("pymongo.MongoClient")
    def test_teardown_databases_drops_configured_test_database(
        self, mock_mongo_client, mock_disconnect_all, mock_super_teardown
    ):
        mock_super_teardown.return_value = "torn-down"

        runner = TestRunner(verbosity=0, interactive=False)
        result = runner.teardown_databases(old_config=("db-config",))

        self.assertEqual(result, "torn-down")
        mock_disconnect_all.assert_called_once_with()
        mock_mongo_client.assert_called_once_with("mongo:27017")
        mock_mongo_client.return_value.drop_database.assert_called_once_with("newsblur_test")
        mock_mongo_client.return_value.close.assert_called_once_with()
        mock_super_teardown.assert_called_once_with(("db-config",))
