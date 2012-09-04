from com.android.monkeyrunner import MonkeyRunner, MonkeyDevice
from com.android.monkeyrunner.easy import EasyMonkeyDevice
from com.android.monkeyrunner.easy import By

class colors:
    OK = '\033[92m'
    KO = '\033[91m'
    END = '\033[0m'
    
class LinphoneTest():
	def __init__(self, test_name):
		self.test_name = test_name
		
		# Connects to the current device
		self.device = MonkeyRunner.waitForConnection()
		self.easyDevice = EasyMonkeyDevice(self.device)
		
	def run(self):
		self.precond()
		
		try:			
			result = self.test()
			if result :
				self.print_result_ok()
			else :
				self.print_result_ko()
				
		except Exception:
			self.print_result_ko()
			
		finally:
			self.postcond()
			
	def find(self, id):
		view = By.id('id/' + id)
		if not view:
			raise Exception("View with id/" + id + " not found")
		return view
		
	def print_result_ok(self):
		print self.test_name + colors.OK + ' OK' + colors.END
		
	def print_result_ko(self):
		print self.test_name + colors.KO + ' KO' + colors.END
		
	def press_back(self):
		self.device.press('KEYCODE_BACK', MonkeyDevice.DOWN_AND_UP)
		
	# Override following methods
	def precond(self):
		pass
		
	def test(self):
		pass
		
	def postcond(self):
		pass
		