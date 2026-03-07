package com.project.openlibrary.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.project.openlibrary.model.User;
import com.project.openlibrary.repository.UserRepository;
import com.project.openlibrary.service.UserService;

@Service
public class UserServiceImpl implements UserService {

	@Autowired
	private UserRepository userRepository;

	@Override
	public void saveUser(User user) {

		this.userRepository.save(user);
		// TODO Auto-generated method stub

	}

	@Override
	public Optional<User> findByEmail(String email) {

		// User isPassMatched = userRepository.findByUsername(email);
		return userRepository.findByEmail(email);
	}

	@Override
	public List<User> getAllUserRecords() {
		// TODO Auto-generated method stub
		return userRepository.findAll();
	}

	@Override
	public void deleteUserbyId(Integer id) {

		this.userRepository.deleteById(id);
	}

}
