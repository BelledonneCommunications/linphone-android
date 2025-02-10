package org.linphone.activities.main.contact.viewmodels

// RxJava BehaviourSubjects won't emit null, so we need a wrapper object that is never null and then
// we can emit that instead
class UserGroupViewModelSubjectWrapper(val userGroupViewModel: UserGroupViewModel?)
