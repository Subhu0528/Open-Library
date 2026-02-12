package com.project.openlibrary.service;

import java.util.List;
import java.util.Optional;

import com.project.openlibrary.model.User;

public interface UserService {

	void saveUser(User user);

	Optional<User> findByEmail(String email);
	
	List< User > getAllUserRecords();
	
	void deleteUserbyId(Integer id);
}
