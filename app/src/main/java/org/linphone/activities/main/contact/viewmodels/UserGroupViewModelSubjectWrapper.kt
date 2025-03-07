package org.linphone.activities.main.contact.viewmodels

// FIXME: replace this with org.linphone.utils.Optional

// RxJava BehaviourSubjects won't emit null, so we need a wrapper object that is never null and then
// we can emit that instead
class UserGroupViewModelSubjectWrapper(val userGroupViewModel: UserGroupViewModel?)
