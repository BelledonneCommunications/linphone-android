from com.android.monkeyrunner import MonkeyRunner, MonkeyDevice
from com.android.monkeyrunner.easy import EasyMonkeyDevice

from lib.LinphoneTest import LinphoneTest

class CallTest(LinphoneTest):
	def precond(self):
		# Run Linphone
		runComponent = 'org.linphone' + '/' + 'org.linphone.setup.LinphoneActivity'
		self.device.startActivity(component=runComponent)
		
		# Be sure to be on dialer screen
		dialer = self.find('dialer')
		self.easyDevice.touch(dialer, MonkeyDevice.DOWN_AND_UP)

	def test(self):
		# Type a SIP address
		address = self.find('Adress')
		self.easyDevice.type(address, 'cotcot@sip.linphone.org')
		self.device.press('KEYCODE_BACK', MonkeyDevice.DOWN_AND_UP)
		
		# Try to call previously typed address
		call = self.find('Call')
		self.easyDevice.touch(call, MonkeyDevice.DOWN_AND_UP)
		MonkeyRunner.sleep(2)
		
		# Check if the call is outgoing correctly
		contact = self.find('contactNameOrNumber')
		return contact
		
	def postcond(self):
		# Stop the call
		hangUp = self.find('hangUp')
		self.easyDevice.touch(hangUp, MonkeyDevice.DOWN_AND_UP)
		
callTest = CallTest('Call')
callTest.run()