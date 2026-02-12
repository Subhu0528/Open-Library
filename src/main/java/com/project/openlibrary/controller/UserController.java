package com.project.openlibrary.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.project.openlibrary.model.User;
import com.project.openlibrary.service.UserService;

import jakarta.validation.Valid;

@Controller
public class UserController {

	@Autowired
	private UserService userService;

	
	@Autowired
    private BCryptPasswordEncoder passwordEncoder; // Inject BCryptPasswordEncoder

 
	
	@PostMapping("/save_user")
	public String saveUser(@Valid @ModelAttribute("user") User user , BindingResult result, Model model) {
		
		if (result.hasErrors()) {
			System.out.println("Validation errors: " + result);
			model.addAttribute("errors", result.getAllErrors());
			return "registerPage";
		}
		
		// Check if email already exists
		var existingUser = userService.findByEmail(user.getEmail());
		if (existingUser.isPresent()) {
			model.addAttribute("error", "Email already registered. Please use a different email or login.");
			return "registerPage";
		}
		
		System.out.println("Registering user: " + user.getEmail());
		String encodedPassword = passwordEncoder.encode(user.getPassword());
		System.out.println("Original password: " + user.getPassword());
		System.out.println("Encoded password: " + encodedPassword);
		
		user.setPassword(encodedPassword);	
		userService.saveUser(user);
		
		System.out.println("User saved successfully!");
		model.addAttribute("message", "Registration successful! Please login.");
		model.addAttribute("user", new User());
		return "userLoginPage";
	}

	@PostMapping("/user_login") //write @Valid for validation
	public String userLogin(@RequestParam("email") String email, 
			@RequestParam("password") String password,
			Model model) {

		System.out.println("Login attempt - Email: " + email);
		
		if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
			model.addAttribute("user", new User());
			model.addAttribute("error", "Email and Password are required");
			return "userLoginPage";
		}

		var dbUser = userService.findByEmail(email);

		 // Check if the user exists
	    if (dbUser.isEmpty()) {
	        System.out.println("User not found with email: " + email);
	        model.addAttribute("user", new User());
	        model.addAttribute("error", "User not found with this email");
	        return "userLoginPage";
	    }

	    User foundUser = dbUser.get();
	    System.out.println("User found: " + foundUser.getEmail());
	    System.out.println("Stored password hash: " + foundUser.getPassword());
	    System.out.println("Password matches: " + passwordEncoder.matches(password, foundUser.getPassword()));
	    
	    // Compare the passwords using BCryptPasswordEncoder
	    if (passwordEncoder.matches(password, foundUser.getPassword())) {
	        System.out.println("Email and Password Matched.");
	        model.addAttribute("user", foundUser);
	        return "userDashBoard";
	    } else {
	        System.out.println("Email and Password Not Matched.");
	        model.addAttribute("user", new User());
	        model.addAttribute("error", "Password is incorrect");
	        return "userLoginPage";
	    }
	}

	// display list of user
	@GetMapping("/showAllUser")
	public String showAll(Model model) {
		model.addAttribute("userList", userService.getAllUserRecords());

		return "showRegisterUser";

	}

	@GetMapping("/deleteUser/{id}")
	public String deleteUser(@PathVariable(value = "id") Integer id) {
		// call delete employee method
		this.userService.deleteUserbyId(id);
		return "redirect:/showAllUser";

	}

	@GetMapping("/userProfile")
	public String userProfile(User user) {
		return "userProfile";
	}

}
