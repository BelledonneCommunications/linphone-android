from com.android.monkeyrunner import MonkeyRunner, MonkeyDevice
from com.android.monkeyrunner.easy import EasyMonkeyDevice

from lib.LinphoneTest import LinphoneTest

class InstallTest(LinphoneTest):
	def test(self):
		# Parameters, must be the same as in the build.xml file
		package = 'org.linphone'
		appname = 'Linphone'
		activity = 'org.linphone.LinphoneLauncherActivity'

		# Installs the Android package. Notice that this method returns a boolean, so you can test
		# to see if the installation worked.
		self.device.installPackage('../bin/' + appname + '-debug.apk')
		
		# sets the name of the component to start
		runComponent = package + '/' + activity
		
		# Runs the component and wait for it to be launched
		self.device.startActivity(component=runComponent)
		MonkeyRunner.sleep(7)
		
		menu = self.find('menu')
		return menu
		
installTest = InstallTest('Install')
installTest.run()