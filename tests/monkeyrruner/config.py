from com.android.monkeyrunner import MonkeyRunner, MonkeyDevice
from com.android.monkeyrunner.easy import EasyMonkeyDevice

from lib.LinphoneTest import LinphoneTest

class ConfigurationTest(LinphoneTest):
	def setAccount(self, username, password, domain):
		self.username = username
		self.password = password
		self.domain = domain

	def precond(self):
		# Run the setup assistant
		runComponent = 'org.linphone' + '/' + 'org.linphone.setup.SetupActivity'
		self.device.startActivity(component=runComponent)
		MonkeyRunner.sleep(2)
		
	def next(self):
		# Press next button
		next = self.find('setup_next')
		self.easyDevice.touch(next, MonkeyDevice.DOWN_AND_UP)
		
	def test(self):
		self.next()
		
		# Choose SIP account
		login = self.find('setup_login_generic')
		self.easyDevice.touch(login, MonkeyDevice.DOWN_AND_UP)
		
		# Fill the fields
		username = self.find('setup_username')
		self.easyDevice.type(username, self.username)
		
		password = self.find('setup_password')
		self.easyDevice.type(password, self.password)
		
		domain = self.find('setup_domain')
		self.easyDevice.type(domain, self.domain)
		
		# Hide the keyboard
		self.press_back()
		
		# Apply config
		apply = self.find('setup_apply')
		self.easyDevice.touch(apply, MonkeyDevice.DOWN_AND_UP)
		
		return True
		
configTest = ConfigurationTest('Account configuration')
configTest.setAccount('monkey', 'cotcot', 'test.linphone.org')
configTest.run()
